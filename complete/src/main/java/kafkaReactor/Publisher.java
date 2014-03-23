package kafkaReactor;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.Reactor;
import reactor.event.Event;

@Service
public class Publisher {
    
    @Autowired
    Reactor reactor;
    
    
    public void sendEvents() throws InterruptedException {
    	int i = 0;
    	while(true) {
            reactor.notify("messages", Event.wrap(new Data(System.currentTimeMillis()+"@ Ibn Gabirol 66,Tel aviv")));
        }

    }

}
