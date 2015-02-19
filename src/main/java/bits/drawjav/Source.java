//package bits.drawjav;
//
//import bits.util.concurrent.ThreadLock;
//
//import java.io.IOException;
//import java.nio.channels.Channel;
//import java.util.List;
//
//
///**
// * @author Philip DeCamp
// */
//public interface Source<T> extends Channel {
//    public static final int POLL_TIMEOUT = -2;
//    public static final int POLL_EOI     = -1;
//    public static final int POLL_NOTHING =  0;
//    public static final int POLL_PACKET  =  1;
//
//    public int available();
//    public int poll( List<? super T> out, long blockMicros ) throws IOException;
//    public boolean canBlock();
//    public ThreadLock lock();
//}
