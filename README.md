This guide walks you through the process of creating an asynchronous, event-driven system using the Reactor project.

What you'll build
-----------------

You'll build an application that fires off events to fetch a random Chuck Norris joke, and then asynchronously gathers them together.

What you'll need
----------------

 - About 15 minutes
 - A favorite text editor or IDE
 - [JDK 6][jdk] or later
 - [Gradle 1.7+][gradle] or [Maven 3.0+][mvn]

[jdk]: http://www.oracle.com/technetwork/java/javase/downloads/index.html
[gradle]: http://www.gradle.org/
[mvn]: http://maven.apache.org/download.cgi


How to complete this guide
--------------------------

Like all Spring's [Getting Started guides](/guides/gs), you can start from scratch and complete each step, or you can bypass basic setup steps that are already familiar to you. Either way, you end up with working code.

To **start from scratch**, move on to [Set up the project](#scratch).

To **skip the basics**, do the following:

 - [Download][zip] and unzip the source repository for this guide, or clone it using [Git][u-git]:
`git clone https://github.com/springframework-meta/gs-messaging-reactor.git`
 - cd into `gs-messaging-reactor/initial`.
 - Jump ahead to [Create a representation for a joke](#initial).

**When you're finished**, you can check your results against the code in `gs-messaging-reactor/complete`.
[zip]: https://github.com/springframework-meta/gs-messaging-reactor/archive/master.zip
[u-git]: /understanding/Git


<a name="scratch"></a>
Set up the project
------------------

First you set up a basic build script. You can use any build system you like when building apps with Spring, but the code you need to work with [Gradle](http://gradle.org) and [Maven](https://maven.apache.org) is included here. If you're not familiar with either, refer to [Building Java Projects with Gradle](/guides/gs/gradle/) or [Building Java Projects with Maven](/guides/gs/maven).

### Create the directory structure

In a project directory of your choosing, create the following subdirectory structure; for example, with `mkdir -p src/main/java/hello` on *nix systems:

    └── src
        └── main
            └── java
                └── hello

### Create a Gradle build file

`build.gradle`
```gradle
buildscript {
    repositories {
        maven { url "http://repo.springsource.org/libs-snapshot" }
        mavenLocal()
    }
}

apply plugin: 'java'
apply plugin: 'eclipse'
apply plugin: 'idea'

jar {
    baseName = 'gs-messaging-reactor'
    version =  '0.1.0'
}

repositories {
    mavenCentral()
    maven { url "http://repo.springsource.org/libs-snapshot" }
}

dependencies {
    compile("org.springframework.boot:spring-boot-starter:0.5.0.BUILD-SNAPSHOT")
    compile("org.projectreactor:reactor-spring:1.0.0.M1")
    compile("com.fasterxml.jackson.core:jackson-databind:2.2.2")
    compile("org.springframework:spring-web:4.0.0.M2")
    testCompile("junit:junit:4.11")
}

task wrapper(type: Wrapper) {
    gradleVersion = '1.7'
}
```

This guide is using [Spring Boot's starter POMs](/guides/gs/spring-boot/).


<a name="initial"></a>
Create a representation for a joke
----------------------------------
For this event-driven example, you'll fetch jokes from [The Internet Chuck Norris Database](http://www.icndb.com/). The [JSON][u-json] format looks like this:

```json
{ 
	"type": "success", 
	"value": { 
		"id": 2, 
		"joke": "MacGyver can build an airplane out of gum and paper clips. 
		         Chuck Norris can kill him and take it.", 
		"categories": [] 
	} 
}
```

The easiest thing to do is capture the inner `value`, i.e. the joke, with one class and then wrap the whole in another class.

`src/main/java/hello/Joke.java`
```java
package hello;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class Joke {

    int id;
    String joke;
    
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getJoke() {
        return joke;
    }
    public void setJoke(String joke) {
        this.joke = joke;
    }
    
}
```
    
This class contains both the `id` and the `joke` text supplied from the website. `@JsonIgnoreProperties(ignoreUnknown=true)` signals that any other attributes are to be ignored.

The wrapper class looks like this:

`src/main/java/hello/JokeResource.java`
```java
package hello;

public class JokeResource {

    String type;
    Joke value;

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public Joke getValue() {
        return value;
    }
    public void setValue(Joke value) {
        this.value = value;
    }

}
```
    
The wrapper class has the `type` attribute along with a `Joke`. This makes it easy later to use Spring's `RestTemplate` and convert JSON to a POJO with the Jackson binding library.

Create a receiver
-----------------

An asynchronous application has publishers and receivers. To create the receiver, implement a receiver with a method to respond to events:

`src/main/java/hello/Receiver.java`
```java
package hello;

import java.util.concurrent.CountDownLatch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import reactor.event.Event;
import reactor.function.Consumer;

@Service
class Receiver implements Consumer<Event<Integer>> {

    @Autowired
    CountDownLatch latch;
    
    RestTemplate restTemplate = new RestTemplate();

    public void accept(Event<Integer> ev) {
        JokeResource jokeResource = restTemplate.getForObject("http://api.icndb.com/jokes/random", JokeResource.class);
        System.out.println("Joke " + ev.getData() + ": " + jokeResource.getValue().getJoke());
        latch.countDown();
    }

}
```

The `Receiver` implements the `Consumer` interface by implementing the `accept()` method. It is geared to receive `Event<Integer>`.

For this example, every time the `Receiver` receives an integer, it fetches another Chuck Norris joke using Spring's `RestTemplate`. Then it signals its completion to the `CountDownLatch` to coordinate when all events have been processed.

`Receiver` has the `@Service` annotation so it will be automatically registered with the [application context][u-application-context].


Create a publisher
------------------

The next step is to publish a handful of events to the reactor.

`src/main/java/hello/Publisher.java`
```java
package hello;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.Reactor;
import reactor.event.Event;

@Service
public class Publisher {
    
    @Autowired
    Reactor reactor;
    
    @Autowired
    CountDownLatch latch;
    
    @Autowired
    Integer numberOfJokes;
    
    public void publishJokes() throws InterruptedException {
        long start = System.currentTimeMillis();
        
        AtomicInteger counter = new AtomicInteger(1);
        
        for (int i=0; i < numberOfJokes; i++) {
            reactor.notify("jokes", Event.wrap(counter.getAndIncrement()));
        }

        latch.await();
        
        long elapsed = System.currentTimeMillis()-start;
        
        System.out.println("Elapsed time: " + elapsed + "ms");
        System.out.println("Average time per joke: " + elapsed/numberOfJokes + "ms");
    }

}
```
    
The code uses a for loop to publish a fixed number of events. An `AtomicInteger` is used to fashion a unique number, which gets turned into a Reactor event with `Event.wrap()`. The event is published to the **jokes** channel using `reactor.notify()`.

> **Note:** Reactor events can contain any type of POJO. This guide uses a very simple integer, but a more detailed event can be used if more information needs to be transmitted to the receiver.

`Receiver` has the `@Service` annotation so it will be automatically registered with the application context.

> **Note:** The code is a bit contrived in that it manually sends a fixed number of integers. In production, this would be replaced by some triggering input, perhaps using Reactor's `TcpServer` to respond to incoming data.

Create an Application class
---------------------------

The final step in putting together your application is to register the components and then invoke them.

`src/main/java/hello/Application.java`
```java
package hello;

import static reactor.event.selector.Selectors.$;

import java.util.concurrent.CountDownLatch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import reactor.core.Environment;
import reactor.core.Reactor;
import reactor.core.spec.Reactors;

@Configuration
@EnableAutoConfiguration
@ComponentScan
public class Application implements CommandLineRunner {

    @Bean
    Environment env() {
        return new Environment();
    }
    
    @Bean
    Reactor createReactor(Environment env) {
        return Reactors.reactor()
                .env(env)
                .dispatcher(Environment.THREAD_POOL)
                .get();
    }
    
    @Autowired
    private Reactor reactor;
    
    @Autowired
    private Receiver receiver;
    
    @Autowired
    private Publisher publisher;
    
    @Bean
    Integer numberOfJokes() {
        return 10;
    }
    
    @Bean
    public CountDownLatch latch(Integer numberOfJokes) {
        return new CountDownLatch(numberOfJokes);
    }
    
    @Override
    public void run(String... args) throws Exception {        
        reactor.on($("jokes"), receiver);
        publisher.publishJokes();
    }
    
    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(Application.class, args);
    }

}
```
    
The Reactor environment is defined with the `environment()` method. The environment gets fed into the `reactor()` method to create an asynchronous reactor. In this case, you are using the `THREAD_POOL` dispatcher.

> **Note:** Reactor has four dispatchers to pick from: **synchronous**, **ring buffer**, **thread pool**, and **event loop**. **Synchronous** is typically used inside a consumer, especially if you use `Stream`s and `Promise`s. **Ring buffer** is used for large volumes of non-blocking events and is based on the [LMAX disruptor](http://martinfowler.com/articles/lmax.html). **Thread pool** is ideal for longer running tasks that might be IO bound, and when it doesn't matter what thread they are run on. **Event loop** is used when you need all events on the exact same thread.

It also defines the number of events to send in the `numberOfJokes()`method and creates a `CountDownLatch` with the `latch()` method. 

The `Application` class is tagged with the `@Configuration` and `@ComponentScan` annotations. This lets it define the application context while also scanning the `hello` package for the `@Service` objects.

The `main()` method fetches the `reactor` and the `receiver`. It then registers the `receiver` to digest events on the "jokes" selector. With everything registered, it uses the `Publisher` to send out a series of joke-fetching events.

The `CountDownLatch` then waits until every thread reports that it's done before proceeding.

Build an executable JAR
-----------------------

Now that your `Application` class is ready, you simply instruct the build system to create a single, executable jar containing everything. This makes it easy to ship, version, and deploy the service as an application throughout the development lifecycle, across different environments, and so forth.

Add the following configuration to your existing Gradle build file:

`build.gradle`
```groovy
buildscript {
    ...
    dependencies {
        classpath("org.springframework.boot:spring-boot-gradle-plugin:0.5.0.BUILD-SNAPSHOT")
    }
}

apply plugin: 'spring-boot'
```

The [Spring Boot gradle plugin][spring-boot-gradle-plugin] collects all the jars on the classpath and builds a single "über-jar", which makes it more convenient to execute and transport your service.
It also searches for the `public static void main()` method to flag as a runnable class.

Now run the following command to produce a single executable JAR file containing all necessary dependency classes and resources:

```sh
$ ./gradlew build
```

Now you can run the JAR by typing:

```sh
$ java -jar build/libs/gs-messaging-reactor-0.1.0.jar
```

[spring-boot-gradle-plugin]: https://github.com/SpringSource/spring-boot/tree/master/spring-boot-tools/spring-boot-gradle-plugin

> **Note:** The procedure above will create a runnable JAR. You can also opt to [build a classic WAR file](/guides/gs/convert-jar-to-war/) instead.


Run the service
-------------------
Run your service at the command line:

```sh
$ ./gradlew clean build && java -jar build/libs/gs-messaging-reactor-0.1.0.jar
```



You should see output similar to this:

```
Joke 7: Chuck Norris doesn't step on toes. Chuck Norris steps on necks.
Joke 4: Thousands of years ago Chuck Norris came across a bear. It was so terrified that it fled north into the arctic. It was also so terrified that all of its decendents now have white hair.
Joke 1: Chuck Norris puts his pants on one leg at a time, just like the rest of us. The only difference is, then he kills people.
Joke 2: Chuck Norris burst the dot com bubble.
Joke 6: The Drummer for Def Leppard's only got one arm. Chuck Norris needed a back scratcher.
Joke 8: The original title for Star Wars was &quot;Skywalker: Texas Ranger&quot;. Starring Chuck Norris.
Joke 3: Chuck Norris can lead a horse to water AND make it drink.
Joke 5: MySpace actually isn't your space, it's Chuck's (he just lets you use it).
Joke 9: Pluto is actually an orbiting group of British soldiers from the American Revolution who entered space after the Chuck gave them a roundhouse kick to the face.
Joke 10: When Chuck Norris break the build, you can't fix it, because there is not a single line of code left.
Elapsed time: 631ms
Average time per joke: 63ms
```
The events were dispatched in order, one through ten. But the output shows that they were consumed asynchronously due to the results being out of order.


Summary
-------
Congratulations! You've just developed an asynchronous, message-driven system using the Reactor project. This is just the beginning of what you can build with it.

[u-json]: /understanding/JSON
[u-application-context]: /understanding/application-context
