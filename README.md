
What you'll build
-----------------

This guide walks you through the process of creating an asynchronous, event-driven system using the Reactor project.


What you'll need
----------------

 - About 15 minutes
 - A favorite text editor or IDE
 - [JDK 6][jdk] or later
 - [Maven 3.0][mvn] or later

[jdk]: http://www.oracle.com/technetwork/java/javase/downloads/index.html
[mvn]: http://maven.apache.org/download.cgi


How to complete this guide
--------------------------

Like all Spring's [Getting Started guides](/guides/gs), you can start from scratch and complete each step, or you can bypass basic setup steps that are already familiar to you. Either way, you end up with working code.

To **start from scratch**, move on to [Set up the project](#scratch).

To **skip the basics**, do the following:

 - [Download][zip] and unzip the source repository for this guide, or clone it using [git](/understanding/git):
`git clone https://github.com/springframework-meta/gs-messaging-reactor.git`
 - cd into `gs-messaging-reactor/initial`.
 - Jump ahead to [Create a representation for a joke](#initial).

**When you're finished**, you can check your results against the code in `gs-messaging-reactor/complete`.
[zip]: https://github.com/springframework-meta/gs-messaging-reactor/archive/master.zip


<a name="scratch"></a>
Set up the project
------------------

First you set up a basic build script. You can use any build system you like when building apps with Spring, but the code you need to work with [Maven](https://maven.apache.org) and [Gradle](http://gradle.org) is included here. If you're not familiar with either, refer to [Building Java Projects with Maven](/guides/gs/maven/content) or [Building Java Projects with Gradle](/guides/gs/gradle/content).

### Create the directory structure

In a project directory of your choosing, create the following subdirectory structure; for example, with `mkdir -p src/main/java/hello` on *nix systems:

    └── src
        └── main
            └── java
                └── hello

### Create a Maven POM

`pom.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>

	<groupId>org.springframework</groupId>
	<artifactId>gs-messaging-reactor</artifactId>
	<version>0.1.0</version>

	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>0.5.0.BUILD-SNAPSHOT</version>
	</parent>

	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter</artifactId>
		</dependency>
		<dependency>
			<groupId>org.projectreactor</groupId>
			<artifactId>reactor-spring</artifactId>
		</dependency>
		<dependency>
			<groupId>com.fasterxml.jackson.core</groupId>
			<artifactId>jackson-databind</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework</groupId>
			<artifactId>spring-web</artifactId>
		</dependency>
	</dependencies>

	<!-- TODO: remove once bootstrap goes GA -->
	<repositories>
		<repository>
			<id>spring-snapshots2</id>
			<url>http://repo.springsource.org/libs-snapshot</url>
			<snapshots>
				<enabled>true</enabled>
			</snapshots>
		</repository>
		<repository>
			<id>spring-release</id>
			<url>http://repo.springsource.org/libs-release</url>
			<snapshots>
				<enabled>false</enabled>
			</snapshots>
		</repository>
	</repositories>
	<pluginRepositories>
		<pluginRepository>
			<id>spring-snapshots</id>
			<url>http://repo.springsource.org/snapshot</url>
			<snapshots>
				<enabled>true</enabled>
			</snapshots>
		</pluginRepository>
	</pluginRepositories>
</project>
```

This guide is using [Spring Boot's starter POMs](/guides/gs/spring-boot/content).

Note to experienced Maven users who are unaccustomed to using an external parent project: you can take it out later, it's just there to reduce the amount of code you have to write to get started.


<a name="initial"></a>
Create a representation for a joke
----------------------------------
For this event-driven example, you'll fetch jokes from [The Internet Chuck Norris Database](http://www.icndb.com/). The [JSON][u-json] format looks like this:

```json
{ 
	"type": "success", 
	"value": { 
		"id": 2, 
		"joke": "MacGyver can build an airplane out of gum and paper clips. Chuck Norris can kill him and take it.", 
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
    
It has the `type` attribute along with a `Joke`. This will make it easy to later on use Spring's `RestTemplate` and convert JSON to a POJO using the Jackson binding library.

Create a receiver
-----------------

In any asynchronous application, there are publishers and receivers. To create the receiver, implement a receiver with a method to respond to events:

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

For this example, every time it receives an integer, it fetches another Chuck Norris joke using Spring's `RestTemplate`. Then it signals its completion to the `CountDownLatch` to coordinate when all events have been processed.

`Receiver` has the `@Service` annotation so it can be automatically registered with the [application context][u-application-context].


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
    
The code uses a for loop to publish a fixed number of events. Each event contains a unique number. It uses an `AtomicInteger` to ensure a unique set of integers.

`Receiver` has the `@Service` annotation so it can be automatically registered with the application context.

> **Note:** The code is a bit contrived in that it manually sends a fixed number of integers. In production, this would be replaced by some triggering input, perhaps using Reactor's `TcpServer` to receive incoming data.

Create an Application
---------------------

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
    
The Reactor environment is defined with the `environment()` method. Then it gets fed into the `reactor()` method to wire up an asynchronous reactor. In this case, you are using the `THREAD_POOL` dispatcher.

> **Note:** Reactor has four dispatchers: **synchronous**, **ring buffer**, **thread pool**, and **event loop**. Synchronous is typically used inside a consumer, especially if you use `Stream`s and `Promise`s. Ring buffer is used for large volumes of non-blocking events and is based on the [LMAX disruptor](http://martinfowler.com/articles/lmax.html). Thread pool is ideal for longer running tasks that might be IO bound, and when it doesn't matter what thread they are run on. Event loop is when you need all events on the exact same thread.

It also defines the number of events to send in the `numberOfJokes()`method and creates a `CountDownLatch` with the `latch()` method. 

This class is tagged with the `@Configuration` and `@ComponentScan` annotations. This lets it define the application context while also scanning the `hello` package for the `@Service` objects.

The `main()` method fetches the `reactor` and the `receiver`. It then registers the `receiver` to digest events on the "jokes" selector. With everything registered, it uses the `Publisher` to send out a series of joke-fetching events.

The `CountDownLatch` then waits until every thread reports that it's done before proceeding.

Build an executable JAR
-----------------------

Now that your `Application` class is ready, you simply instruct the build system to create a single, executable jar containing everything. This makes it easy to ship, version, and deploy the service as an application throughout the development lifecycle, across different environments, and so forth.

Add the following configuration to your existing Maven POM:

`pom.xml`
```xml
    <properties>
        <start-class>hello.Application</start-class>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
```

The `start-class` property tells Maven to create a `META-INF/MANIFEST.MF` file with a `Main-Class: hello.Application` entry. This entry enables you to run it with `mvn spring-boot:run` (or simply run the jar itself with `java -jar`).

The [Spring Boot maven plugin][spring-boot-maven-plugin] collects all the jars on the classpath and builds a single "über-jar", which makes it more convenient to execute and transport your service.

Now run the following command to produce a single executable JAR file containing all necessary dependency classes and resources:

```sh
$ mvn package
```

[spring-boot-maven-plugin]: https://github.com/SpringSource/spring-boot/tree/master/spring-boot-maven-plugin

> **Note:** The procedure above will create a runnable JAR. You can also opt to [build a classic WAR file](/guides/gs/convert-jar-to-war/content) instead.


Run the service
-------------------
Run your service using the spring-boot plugin at the command line:

```sh
$ mvn spring-boot:run
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
The events were dispatched in order, one through ten. But the output shows that they were consumed asynchronously due the results being out of order.


Summary
-------
Congrats! You've just developed an asynchronous, message-driven system using the Reactor project. This is just the beginning of what you can build with it.

[u-json]: /u/json
[u-application-context]: /u/application-context
