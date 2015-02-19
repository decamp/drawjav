//package bits.drawjav;
//
//import bits.util.concurrent.ThreadLock;
//
//import java.io.IOException;
//import java.util.List;
//
//
///**
// * @author Philip DeCamp
// */
//public class QueuePipe<T> implements Sink<T>, PullSource<T> {
//
//
//    QueuePipe( int capacity, ThreadLock optLock ) {
//
//    }
//
//
//    @Override
//    public Object lock() {
//        return null;
//    }
//
//    @Override
//    public void pull( long blockMicros, List<? super T> out ) throws IOException {
//
//    }
//
//    @Override
//    public void consume( T packet ) throws IOException {
//
//    }
//
//    @Override
//    public void clear() {
//
//    }
//
//    @Override
//    public boolean isOpen() {
//        return false;
//    }
//
//    @Override
//    public void close() throws IOException {
//
//    }
//}
