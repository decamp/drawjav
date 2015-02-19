//package bits.drawjav;
//
//import bits.util.concurrent.ThreadLock;
//
//import java.io.IOException;
//import java.nio.channels.Channel;
//import java.util.List;
//
///**
// * @author Philip DeCamp
// */
//public interface Pipe<T> extends PipeUnit {
//
//    /**
//     * @param packet      Input to process. Pass {@code null} to flush pipe.
//     * @param out         Receives output packets.
//     * @return True if input <em>packet</em> is completely processed and pipe is ready for next packet.
//     *         If input {@code packet == null}, then {@code true} is returned to indicate pipe is completely
//     *         drained.
//     * @throws IOException
//     */
//    public Result process( T packet, List<? super T> out ) throws IOException;
//
//}
