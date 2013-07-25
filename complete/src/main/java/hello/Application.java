package hello;

import static reactor.event.selector.Selectors.$;

import java.util.concurrent.CountDownLatch;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import reactor.core.Environment;
import reactor.core.Reactor;
import reactor.core.spec.Reactors;

@Configuration
@ComponentScan
public class Application {
	
	@Bean
	public Environment environment() {
		return new Environment();
	}
	
	@Bean
	public Reactor reactor(Environment env) {
		return Reactors.reactor()
				.env(env)
				.dispatcher(Environment.THREAD_POOL)
				.get();
	}
	
	@Bean
	Integer numberOfJokes() {
		return 10;
	}
	
	@Bean
	public CountDownLatch latch(Integer numberOfJokes) {
		return new CountDownLatch(numberOfJokes);
	}
	
	public static void main(String[] args) throws InterruptedException {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Application.class);
		
		Reactor reactor = ctx.getBean(Reactor.class);
		Receiver receiver = ctx.getBean(Receiver.class);
		reactor.on($("jokes"), receiver);
		
		ctx.getBean(Publisher.class).publishJokes();
		
		ctx.close();
	}

}
