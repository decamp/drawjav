/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.*;

import bits.draw3d.DrawEnv;
import bits.draw3d.DrawNodeAdapter;
import bits.drawjav.audio.AudioFormat;
import bits.drawjav.audio.AudioPacket;
import bits.drawjav.video.PictureFormat;
import bits.drawjav.video.VideoPacket;
import bits.microtime.*;

/**
 * Handles realtime processing of one source.
 * 
 * @author decamp
 */
public class SyncedDriver extends DrawNodeAdapter implements StreamDriver {
    
    
    private final PlayController mPlayCont;
    private final PassiveDriver mDriver;
    private final PlayHandler mPlayHandler;
    
        
    public SyncedDriver( PlayController playCont, Source source ) {
        mPlayCont    = playCont;
        mDriver      = new PassiveDriver( source );
        mPlayHandler = new PlayHandler();
        playCont.caster().addListener( mPlayHandler );
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


    public Source source() {
        return mDriver.source();
    }
    
    
    public PlayController playController() {
        return mPlayCont;
    }
    

    public StreamHandle openVideoStream( StreamHandle source,
                                         PictureFormat destFormat,
                                         Sink<? super VideoPacket> sink )
                                         throws IOException 
    {
        return mDriver.openVideoStream( source, destFormat, sink );
    }
    
    
    public StreamHandle openAudioStream( StreamHandle source,
                                         AudioFormat format,
                                         Sink<? super AudioPacket> sink )
                                         throws IOException 
    {
        return mDriver.openAudioStream( source, format, sink );
    }
    
    
    public boolean closeStream( StreamHandle stream ) throws IOException {
        return mDriver.closeStream( stream );
    }
    
    
    public void close() throws IOException {
        mDriver.close();
        mPlayCont.caster().removeListener( mPlayHandler );
    }
    
    
    public boolean isOpen() {
        return mDriver.isOpen();
    }
    
    @Override
    public void pushDraw( DrawEnv d ) {
        long timeMicros = mPlayCont.clock().micros();
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
    
    
        
    
    private final class PlayHandler implements PlayControl {

        public void playStart( long execMicros ) {}

        public void playStop( long execMicros ) {}

        public void seek( long execMicros, long seekMicros ) {
            mDriver.seek( seekMicros );
        }

        public void setRate( long execMicros, double rate ) {}
        
    }
    
}
