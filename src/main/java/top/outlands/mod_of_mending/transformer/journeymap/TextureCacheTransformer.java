package top.outlands.mod_of_mending.transformer.journeymap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import top.outlands.foundation.IExplicitTransformer;

import java.util.ArrayList;
import java.util.List;

/**
 * JourneyMap 6.0.x 的 TextureCache#collectClasspathTextures 用
 * JarURLConnection.getJarFile() 取到 JDK 全局 jar 缓存里的共享 JarFile，
 * 却在 finally 里把它 close() 掉。ActualClassLoader#jarInfoFor 缓存的正是同一个实例，
 * 之后所有懒加载类都会抛 IllegalStateException: zip file closed。
 * 这里把该 close() 调用替换为 POP（栈平衡不变），避免关闭共享句柄。
 */
public class TextureCacheTransformer implements IExplicitTransformer {

    @Override
    public byte[] transform(byte[] bytes) {
        ClassNode classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, 0);

        if (classNode.methods != null) {
            for (MethodNode methodNode : classNode.methods) {
                InsnList instructions = methodNode.instructions;
                if (instructions == null) continue;

                List<AbstractInsnNode> targets = new ArrayList<>();
                for (AbstractInsnNode insnNode : instructions) {
                    if (insnNode.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && insnNode instanceof MethodInsnNode methodInsnNode) {
                        if ("java/util/jar/JarFile".equals(methodInsnNode.owner)
                                && "close".equals(methodInsnNode.name)
                                && "()V".equals(methodInsnNode.desc)) {
                            targets.add(insnNode);
                        }
                    }
                }
                for (AbstractInsnNode target : targets) {
                    instructions.set(target, new InsnNode(Opcodes.POP));
                }
            }
        }

        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        return classWriter.toByteArray();
    }
}
