package bits.drawjav.pipe;

import bits.drawjav.MemoryManager;
import bits.drawjav.audio.AudioPacket;
import bits.microtime.Frac;
import bits.microtime.SyncClockControl;

import java.io.IOException;
import java.util.List;


/**
 * @author Philip DeCamp
 */
public class EmptyPacketSplitter implements Pipe<AudioPacket>, SyncClockControl {


    public EmptyPacketSplitter( MemoryManager optMem ) {}

    @Override
    public int process( AudioPacket packet, List<? super AudioPacket> out ) throws IOException {
        return 0;
    }

    @Override
    public void clear() {}

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public void clockStart( long execMicros ) {

    }

    @Override
    public void clockStop( long execMicros ) {

    }

    @Override
    public void clockSeek( long execMicros, long seekMicros ) {

    }

    @Override
    public void clockRate( long execMicros, Frac rate ) {

    }

}
