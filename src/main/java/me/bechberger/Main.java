package me.bechberger;

import java.lang.instrument.Instrumentation;
import java.util.*;

public class Main {

    private static Node cumulative = new Node("[cumulative]", List.of());

    record Node(String name, List<String> packages, Node parent, Map<ClassLoader, Node> children, Thread thread) {

        public Node(String name, List<String> packages) {
            this(name, packages, null, new HashMap<>(), null);
        }

        public Node(String name, List<String> packages, Node parent, Thread thread) {
            this(name, packages, parent, new HashMap<>(), thread);
        }

        public Node getChild(ClassLoader classLoader, Thread thread) {
            Node node = children.computeIfAbsent(classLoader,
                    cl -> new Node(cl.getName(), new ArrayList<>(),
                            this, thread));
            node.packages.clear();
            node.packages.addAll(Arrays.stream(classLoader.getDefinedPackages()).map(Package::getName).sorted(Comparator.comparingInt(p -> p.split("\\.").length)).toList());
            return node;
        }

        public void add(List<ClassLoader> reversedClassLoaderChain, Thread thread) {
            add(reversedClassLoaderChain, thread, reversedClassLoaderChain.size());
        }

        public void add(List<ClassLoader> reversedClassLoaderChain, Thread thread, int end) {
            if (end == 0) {
                return;
            }
            ClassLoader classLoader = reversedClassLoaderChain.get(end - 1);
            Node child = getChild(classLoader, thread);
            child.add(reversedClassLoaderChain, null, end - 1);
        }

        void print(int maxPackages, String indent) {
            System.out.println(indent + name);
            if (thread != null) {
                System.out.println(indent + "  Thread: " + thread.getName());
            }
            packages.stream().limit(maxPackages).forEach(p -> System.out.println(indent + "     " + p));
            for (Node child : children.values()) {
                child.print(maxPackages, indent + "  ");
            }
        }

        void print(int maxPackages) {
            print(maxPackages, "");
        }
    }

    static final int DEFAULT_MAX_PACKAGES = 5;

    record Options(int maxPackages, int everyNSeconds) {
    }

    static void printUsage() {
        System.err.println("Usage: java -javaagent:classloader-hierarchy-agent.jar[=maxPackages=10,everyNSeconds=0] " +
                "<main class>");
        System.err.println("  maxPackages: maximum number of packages to print per classloader");
        System.err.println("  every: print the hierarchy every N seconds (0 to disable)");
    }

    static Options parseArguments(String agentArgs) {
        if (agentArgs == null) {
            return new Options(DEFAULT_MAX_PACKAGES, 0);
        }
        String[] args = agentArgs.split(",");
        int maxPackages = DEFAULT_MAX_PACKAGES;
        int everyNSeconds = 0;
        for (String arg : args) {
            String[] keyValue = arg.split("=");
            if (keyValue.length != 2) {
                System.err.println("Invalid argument: " + arg);
                printUsage();
                continue;
            }
            String key = keyValue[0];
            String value = keyValue[1];
            switch (key) {
                case "maxPackages" -> maxPackages = Integer.parseInt(value);
                case "every" -> everyNSeconds = Integer.parseInt(value);
                default -> {
                    System.err.println("Invalid argument: " + arg);
                    printUsage();
                }
            }
        }
        return new Options(maxPackages, everyNSeconds);
    }


    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        Options options = parseArguments(agentArgs);
        iteration(options.maxPackages);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> iteration(options.maxPackages)));
        if (options.everyNSeconds > 0) {
            new Timer().scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    iteration(options.maxPackages);
                }
            }, options.everyNSeconds * 1000L, options.everyNSeconds * 1000L);
        }
    }

    static void iteration(int maxPackages) {
        obtainTree().print(maxPackages);
        updateTree(cumulative);
        cumulative.print(maxPackages);
    }

    static Node obtainTree() {
        Node root = new Node("[root]", List.of());
        updateTree(root);
        return root;
    }

    static void updateTree(Node root) {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            List<ClassLoader> reversedClassLoaderChain = obtainReversedClassLoaderChain(thread.getContextClassLoader());
            root.add(reversedClassLoaderChain, thread);
        }
    }

    static List<ClassLoader> obtainReversedClassLoaderChain(ClassLoader classLoader) {
        List<ClassLoader> result = new ArrayList<>();
        while (classLoader != null) {
            result.add(classLoader);
            classLoader = classLoader.getParent();
        }
        return result;
    }
}