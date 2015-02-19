//package bits.drawjav;
//
//import java.io.IOException;
//import java.nio.channels.Channel;
//
//
///**
// * @author Philip DeCamp
// */
//public interface PipeUnit extends Channel {
//
//    public enum Result {
//        INCOMPLETE,
//        DONE,
//        NOTHING_TO_DO,
//        TIMEOUT
//    }
//
//    /**
//     * Clears all state.
//     */
//    public void clear();
//
//    /**
//     * Closes pipe, releasing any resources. Pipe is not usable after closing.
//     *
//     * @throws java.io.IOException
//     */
//    public void close() throws IOException;
//
//    /**
//     * @return true if PipeUnit is open and usable.
//     */
//    public boolean isOpen();
//
//}
