package cogmac.drawjav;

import java.io.*;
import java.util.logging.*;

import javax.media.opengl.GL;

import cogmac.clocks.*;
import cogmac.draw3d.nodes.DrawNodeAdapter;


/**
 * Handles realtime processing of one source.
 * 
 * @author decamp
 */
public class SyncedDriver extends DrawNodeAdapter implements StreamDriver {
    
        
    private static Logger sLog = Logger.getLogger( SyncedDriver.class.getName() );
    
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
    
    
    public void videoPoolCap( int poolCap ) {
        mDriver.videoPoolCap( poolCap );
    }
    
    
    public void audioPoolCap( int cap ) {
        mDriver.audioPoolCap( cap );
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
    
    
    public void pushDraw( GL gl ) {
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
    
    

    private synchronized void warn( String message, Exception ex ) {
        sLog.log( Level.WARNING, message, ex );
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
