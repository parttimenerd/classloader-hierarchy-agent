Classloader Hierarchy Agent
===========================

An agent to print the classloader hierarchy of a running JVM (16+).

To lear more on this topic, please consider reading the accompanying blog post
[Class Loader Hierarchies](https://mostlynerdless.de/blog/2023/06/02/class-loader-hierarchies/).

Now, we no further ado, let's look at how to use the agent for an example:

```sh
# build it
mvn package

# download a benchmark
test -e renaissance.jar || wget https://github.com/renaissance-benchmarks/renaissance/releases/download/v0.14.2/renaissance-gpl-0.14.2.jar -O renaissance.jar

# run it
java -javaagent:target/classloader-hierarchy-agent.jar -jar renaissance.jar finagle-http -r 1
```

This results in the following output:

```
...
[root]
  platform
    Thread: finagle/netty4-2-6
       java.sql
       sun.util.resources.provider
       sun.text.resources.cldr.ext
       sun.util.resources.cldr.provider
    app
         me.bechberger
         org.renaissance
         org.renaissance.core
      null
           scala
           scala.collection
           scala.jdk
           scala.io
           scala.runtime
[cumulative]   # accumulates the classloaders over time
  platform
    Thread: main
       java.sql
       sun.util.resources.provider
       sun.text.resources.cldr.ext
       sun.util.resources.cldr.provider
    ...
```

Be aware that the bootstrap classloader is always implicitly the root of the hierarchy, but it not printed
as it represented as `null`. To quote from the documentation of `Class.getClassLoader()`:

> Returns the class loader for the class. Some implementations may use null to represent the bootstrap class loader. 
> This method will return null in such implementations if this class was loaded by the bootstrap class loader.

The agent has two options:

```
Usage: java -javaagent:classloader-hierarchy-agent.jar[=maxPackages=10,everyNSeconds=0] <main class>
  maxPackages: maximum number of packages to print per classloader
  every: print the hierarchy every N seconds (0 to disable)
```

License
-------
Copyright (c) 2023 SAP SE or an SAP affiliate company, Johannes Bechberger
and classloader hierarchy agent contributors