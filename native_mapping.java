import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

import org.objectweb.asm.*;

public class JpiSnippet {

    static final String JAR =
        "C:\\Users\\Library\\Documents\\jdk-21_windows-x64_bin\\jdk-21.0.10\\bin\\.minecraft\\mods\\hachimi-b4-crk.jar";

    static final String REG_OWNER =
        "skidonion/vLZkx/___";

    static final String REG_NAME = "___";

    static final String REG_DESC =
        "(ILjava/lang/Class;)V";

    static class Entry {
        String clazz;
        String callerMethod;
        String callerDesc;

        Integer registrationId;
        String registeredClass;

        List<String> natives = new ArrayList<>();
    }

    public static void execute(PrintStream out) {

        try {

            Map<String, Entry> result =
                new LinkedHashMap<>();

            try (JarFile jar = new JarFile(JAR)) {

                Enumeration<JarEntry> entries =
                    jar.entries();

                while (entries.hasMoreElements()) {

                    JarEntry je =
                        entries.nextElement();

                    if (!je.getName().endsWith(".class"))
                        continue;

                    byte[] data;

                    try (InputStream in =
                             jar.getInputStream(je)) {

                        data = in.readAllBytes();
                    }

                    ClassReader cr =
                        new ClassReader(data);

                    cr.accept(
                        new ClassVisitor(Opcodes.ASM9) {

                            String owner;

                            @Override
                            public void visit(
                                int version,
                                int access,
                                String name,
                                String signature,
                                String superName,
                                String[] interfaces) {

                                owner = name;

                                result
                                    .computeIfAbsent(
                                        name,
                                        x -> {
                                            Entry e =
                                                new Entry();

                                            e.clazz =
                                                x.replace(
                                                    '/',
                                                    '.'
                                                );

                                            return e;
                                        }
                                    );
                            }

                            @Override
                            public MethodVisitor visitMethod(
                                int access,
                                String name,
                                String descriptor,
                                String signature,
                                String[] exceptions) {

                                Entry e =
                                    result.get(owner);

                                if ((access &
                                     Opcodes.ACC_NATIVE) != 0) {

                                    e.natives.add(
                                        name + descriptor
                                    );
                                }

                                return new MethodVisitor(
                                    Opcodes.ASM9
                                ) {

                                    Integer lastInt;
                                    String lastClass;

                                    @Override
                                    public void visitInsn(
                                        int opcode) {

                                        switch (opcode) {

                                            case Opcodes.ICONST_M1:
                                                lastInt = -1;
                                                break;

                                            case Opcodes.ICONST_0:
                                            case Opcodes.ICONST_1:
                                            case Opcodes.ICONST_2:
                                            case Opcodes.ICONST_3:
                                            case Opcodes.ICONST_4:
                                            case Opcodes.ICONST_5:

                                                lastInt =
                                                    opcode -
                                                    Opcodes.ICONST_0;

                                                break;
                                        }
                                    }

                                    @Override
                                    public void visitIntInsn(
                                        int opcode,
                                        int operand) {

                                        if (opcode ==
                                                Opcodes.BIPUSH ||
                                            opcode ==
                                                Opcodes.SIPUSH) {

                                            lastInt =
                                                operand;
                                        }
                                    }

                                    @Override
                                    public void visitLdcInsn(
                                        Object value) {

                                        if (value
                                                instanceof Type t &&
                                            t.getSort() ==
                                                Type.OBJECT) {

                                            lastClass =
                                                t.getClassName();
                                        }

                                        if (value
                                                instanceof Integer i) {

                                            lastInt = i;
                                        }
                                    }

                                    @Override
                                    public void visitMethodInsn(
                                        int opcode,
                                        String targetOwner,
                                        String targetName,
                                        String targetDesc,
                                        boolean isInterface) {

                                        if (
                                            REG_OWNER.equals(
                                                targetOwner
                                            ) &&
                                            REG_NAME.equals(
                                                targetName
                                            ) &&
                                            REG_DESC.equals(
                                                targetDesc
                                            )
                                        ) {

                                            e.registrationId =
                                                lastInt;

                                            e.registeredClass =
                                                lastClass;

                                            e.callerMethod =
                                                name;

                                            e.callerDesc =
                                                descriptor;
                                        }
                                    }
                                };
                            }

                        },
                        ClassReader.SKIP_FRAMES
                    );
                }
            }

            Path desktop =
                Paths.get(
                    System.getProperty(
                        "user.home"
                    ),
                    "Desktop"
                );

            Files.createDirectories(
                desktop
            );

            Path output =
                desktop.resolve(
                    "native_mapping.json"
                );

            String json =
                toJson(result);

            Files.writeString(
                output,
                json,
                StandardCharsets.UTF_8
            );

            out.println(
                "Saved:"
            );

            out.println(
                output.toAbsolutePath()
            );

            long registered =
                result.values()
                    .stream()
                    .filter(
                        x ->
                            x.registrationId != null ||
                            x.registeredClass != null
                    )
                    .count();

            long nativeClasses =
                result.values()
                    .stream()
                    .filter(
                        x -> !x.natives.isEmpty()
                    )
                    .count();

            out.println(
                "Registered classes: "
                    + registered
            );

            out.println(
                "Native classes: "
                    + nativeClasses
            );

        } catch (Throwable t) {
            t.printStackTrace(out);
        }
    }

    static String toJson(
        Map<String, Entry> entries) {

        StringBuilder sb =
            new StringBuilder();

        sb.append("{\n");
        sb.append(
            "  \"registrar\": \"skidonion.vLZkx.___.___(ILjava/lang/Class;)V\",\n"
        );
        sb.append(
            "  \"classes\": [\n"
        );

        boolean first =
            true;

        for (Entry e :
                entries.values()) {

            if (e.natives.isEmpty() &&
                e.registrationId == null &&
                e.registeredClass == null) {

                continue;
            }

            if (!first)
                sb.append(",\n");

            first = false;

            sb.append("    {\n");

            sb.append(
                "      \"class\": "
            );

            quote(
                sb,
                e.clazz
            );

            sb.append(",\n");

            sb.append(
                "      \"registrationId\": "
            );

            if (e.registrationId == null)
                sb.append("null");
            else
                sb.append(
                    e.registrationId
                );

            sb.append(",\n");

            sb.append(
                "      \"registeredClass\": "
            );

            quoteNullable(
                sb,
                e.registeredClass
            );

            sb.append(",\n");

            sb.append(
                "      \"registrationCaller\": "
            );

            if (e.callerMethod == null) {

                sb.append("null");

            } else {

                quote(
                    sb,
                    e.callerMethod +
                    e.callerDesc
                );
            }

            sb.append(",\n");

            sb.append(
                "      \"nativeMethods\": ["
            );

            for (
                int i = 0;
                i < e.natives.size();
                i++
            ) {

                if (i != 0)
                    sb.append(", ");

                quote(
                    sb,
                    e.natives.get(i)
                );
            }

            sb.append("]\n");

            sb.append("    }");
        }

        sb.append("\n  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    static void quoteNullable(
        StringBuilder sb,
        String s) {

        if (s == null)
            sb.append("null");
        else
            quote(sb, s);
    }

    static void quote(
        StringBuilder sb,
        String s) {

        sb.append('"');

        for (
            int i = 0;
            i < s.length();
            i++
        ) {

            char c =
                s.charAt(i);

            switch (c) {

                case '\\':
                    sb.append("\\\\");
                    break;

                case '"':
                    sb.append("\\\"");
                    break;

                case '\n':
                    sb.append("\\n");
                    break;

                case '\r':
                    sb.append("\\r");
                    break;

                case '\t':
                    sb.append("\\t");
                    break;

                default:

                    if (c < 32) {

                        sb.append(
                            String.format(
                                "\\u%04x",
                                (int)c
                            )
                        );

                    } else {

                        sb.append(c);
                    }
            }
        }

        sb.append('"');
    }
}