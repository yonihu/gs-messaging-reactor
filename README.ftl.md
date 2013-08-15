<#assign project_id="gs-messaging-reactor">
This guide walks you through the process of creating an asynchronous, event-driven system using the Reactor project.

What you'll build
-----------------

You'll build an application that fires off events to fetch a random Chuck Norris joke, and then asynchronously gathers them together.

What you'll need
----------------

 - About 15 minutes
 - <@prereq_editor_jdk_buildtools/>


## <@how_to_complete_this_guide jump_ahead='Create a representation for a joke'/>


<a name="scratch"></a>
Set up the project
------------------

<@build_system_intro/>

<@create_directory_structure_hello/>

### Create a Maven POM

    <@snippet path="pom.xml" prefix="initial"/>

<@bootstrap_starter_pom_disclaimer/>


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

    <@snippet path="src/main/java/hello/Joke.java" prefix="complete"/>
    
This class contains both the `id` and the `joke` text supplied from the website. `@JsonIgnoreProperties(ignoreUnknown=true)` signals that any other attributes are to be ignored.

The wrapper class looks like this:

    <@snippet path="src/main/java/hello/JokeResource.java" prefix="complete"/>
    
The wrapper class has the `type` attribute along with a `Joke`. This makes it easy later to use Spring's `RestTemplate` and convert JSON to a POJO with the Jackson binding library.

Create a receiver
-----------------

An asynchronous application has publishers and receivers. To create the receiver, implement a receiver with a method to respond to events:

    <@snippet path="src/main/java/hello/Receiver.java" prefix="complete"/>

The `Receiver` implements the `Consumer` interface by implementing the `accept()` method. It is geared to receive `Event<Integer>`.

For this example, every time the `Receiver` receives an integer, it fetches another Chuck Norris joke using Spring's `RestTemplate`. Then it signals its completion to the `CountDownLatch` to coordinate when all events have been processed.

`Receiver` has the `@Service` annotation so it will be automatically registered with the [application context][u-application-context].


Create a publisher
------------------

The next step is to publish a handful of events to the reactor.

    <@snippet path="src/main/java/hello/Publisher.java" prefix="complete"/>
    
The code uses a for loop to publish a fixed number of events. An `AtomicInteger` is used to fashion a unique number, which gets turned into a Reactor event with `Event.wrap()`. The event is published to the **jokes** channel using `reactor.notify()`.

> **Note:** Reactor events can contain any type of POJO. This guide uses a very simple integer, but a more detailed event can be used if more information needs to be transmitted to the receiver.

`Receiver` has the `@Service` annotation so it will be automatically registered with the application context.

> **Note:** The code is a bit contrived in that it manually sends a fixed number of integers. In production, this would be replaced by some triggering input, perhaps using Reactor's `TcpServer` to respond to incoming data.

Create an Application class
---------------------------

The final step in putting together your application is to register the components and then invoke them.

    <@snippet path="src/main/java/hello/Application.java" prefix="complete"/>
    
The Reactor environment is defined with the `environment()` method. The environment gets fed into the `reactor()` method to create an asynchronous reactor. In this case, you are using the `THREAD_POOL` dispatcher.

> **Note:** Reactor has four dispatchers to pick from: **synchronous**, **ring buffer**, **thread pool**, and **event loop**. **Synchronous** is typically used inside a consumer, especially if you use `Stream`s and `Promise`s. **Ring buffer** is used for large volumes of non-blocking events and is based on the [LMAX disruptor](http://martinfowler.com/articles/lmax.html). **Thread pool** is ideal for longer running tasks that might be IO bound, and when it doesn't matter what thread they are run on. **Event loop** is used when you need all events on the exact same thread.

It also defines the number of events to send in the `numberOfJokes()`method and creates a `CountDownLatch` with the `latch()` method. 

The `Application` class is tagged with the `@Configuration` and `@ComponentScan` annotations. This lets it define the application context while also scanning the `hello` package for the `@Service` objects.

The `main()` method fetches the `reactor` and the `receiver`. It then registers the `receiver` to digest events on the "jokes" selector. With everything registered, it uses the `Publisher` to send out a series of joke-fetching events.

The `CountDownLatch` then waits until every thread reports that it's done before proceeding.

<@build_an_executable_jar_mainhead/>

<@build_an_executable_jar/>


<@run_the_application_with_maven/>


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

<@u_json/>
<@u_application_context/>
