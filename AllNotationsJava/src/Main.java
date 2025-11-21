import all_notations.java_sample00.model.ContextImpl;
import all_notations.java_sample00.model.ContextImpl.EventId;

public class Main {
    public static void main(String[] args) {
    	ContextImpl context = new ContextImpl(null, null, null, null, null, null, null);
    	context.Start();
    	context.EventProc(EventId.E1, null);
    	context.EventProc(EventId.E1, null);
    	context.EventProc(EventId.E0, null);
    	context.EventProc(EventId.E5, null);
    }
}
