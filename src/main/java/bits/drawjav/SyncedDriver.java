/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.*;

import bits.drawjav.audio.AudioFormat;
import bits.drawjav.video.PictureFormat;
import bits.microtime.*;

/**
 * Handles realtime processing of one source.
 * 
 * @author decamp
 */
public class SyncedDriver implements StreamDriver, Ticker {

    private final PlayClock     mClock;
    private final PassiveDriver mDriver;
    private final PlayHandler   mPlayHandler;


    public SyncedDriver( PlayClock clock, PacketReader source ) {
        mClock = clock;
        mDriver = new PassiveDriver( source );
        mPlayHandler = new PlayHandler();
        mClock.addListener( mPlayHandler );
    }


    public void start() {}


    public long seekWarmupMicros() {
        return mDriver.seekWarmupMicros();
    }


    public void seekWarmupMicros( long micros ) {
        mDriver.seekWarmupMicros( micros );
    }


    public MemoryManager memoryManager() {
        return mDriver.memoryManager();
    }


    public void memoryManager( MemoryManager mem ) {
        mDriver.memoryManager( mem );
    }


    public PacketReader source() {
        return mDriver.source();
    }
    
    
    public PlayClock clock() {
        return mClock;
    }
    

    public StreamHandle openVideoStream( PacketReader source,
                                         StreamHandle stream,
                                         PictureFormat destFormat,
                                         Sink<? super DrawPacket> sink )
                                         throws IOException 
    {
        return mDriver.openVideoStream( source, stream, destFormat, sink );
    }
    
    
    public StreamHandle openAudioStream( PacketReader source,
                                         StreamHandle stream,
                                         AudioFormat format,
                                         Sink<? super DrawPacket> sink )
                                         throws IOException 
    {
        return mDriver.openAudioStream( source, stream, format, sink );
    }
    
    
    public boolean closeStream( StreamHandle stream ) throws IOException {
        return mDriver.closeStream( stream );
    }
    
    
    public void close() throws IOException {
        mDriver.close();
        mClock.removeListener( mPlayHandler );
    }
    
    
    public boolean isOpen() {
        return mDriver.isOpen();
    }


    public boolean hasSink() {
        return mDriver.hasSink();
    }

    @Override
    public void tick() {
        long timeMicros = mClock.micros();
        while( true ) {
            if( !mDriver.hasNext() ) {
                return;
            }
            if( mDriver.readPacket() ) {
                if( mDriver.currentMicros() > timeMicros ) {
                    return;
                }
                mDriver.sendCurrent();
            }
        }
    }

    
    private final class PlayHandler implements SyncClockControl {

        public void clockStart( long execMicros ) {}

        public void clockStop( long execMicros ) {}

        public void clockSeek( long execMicros, long seekMicros ) {
            mDriver.seek( seekMicros );
        }

        public void clockRate( long execMicros, Frac rate ) {}
        
    }
    
}
