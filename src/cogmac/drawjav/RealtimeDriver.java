package cogmac.drawjav;

import java.io.*;
import java.util.logging.*;
import cogmac.clocks.*;

/**
 * Handles realtime processing of one source.
 * 
 * @author decamp
 */
@SuppressWarnings( { "unchecked", "rawtypes" } )
public class RealtimeDriver implements StreamDriver {
    
    public static RealtimeDriver newInstance( PlayController playCont,
                                              Source source ) 
    {
        return newInstance( playCont, source, null );
    }
    
    
    public static RealtimeDriver newInstance( PlayController playCont,
                                              Source source,
                                              PacketScheduler syncer ) 
    {
        if( syncer == null ) {
            syncer = new PacketScheduler( playCont );
        }
        return new RealtimeDriver( playCont, source, syncer );
    }
    
    
    private static Logger sLog = Logger.getLogger( RealtimeDriver.class.getName() );
    
    private final PlayController mPlayCont;
    private final PassiveDriver mDriver;
    private final PacketScheduler mSyncer;
    private final PlayHandler mPlayHandler;
    private final ThreadLock mLock;
    private final Thread mThread;
        
    
    private RealtimeDriver( PlayController playCont,
                            Source source,
                            PacketScheduler syncer )
    {
        mPlayCont    = playCont;
        mDriver      = new PassiveDriver( source );
        mSyncer      = syncer;
        mPlayHandler = new PlayHandler();
        mLock        = new ThreadLock();
        mPlayCont.caster().addListener( mPlayHandler );
        //mDriver.seek( mPlayCont.clock().micros() );
        
        mThread = new Thread( RealtimeDriver.class.getSimpleName() ) {
            public void run() {
                runLoop();
            }
        };
        
        mThread.setDaemon( true );
        mThread.setPriority( Thread.NORM_PRIORITY - 1 );
    }
    
    
    
    public void start() {
        synchronized( mLock ) {
            if( mThread != null && !mThread.isAlive() ) {
                mThread.start();
            }
        }
    }
    
    
    public void seekWarmupMicros( long micros ) {
        mDriver.seekWarmupMicros( micros );
    }
    
    
    public void videoPoolCap( int cap ) {
        mDriver.videoPoolCap( cap );
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
        synchronized( mLock ) {
            boolean success = false;
            mSyncer.openPipe( sink, mLock );
            Sink syncedSink = mSyncer.openPipe( sink, mLock );
            
            try {
                StreamHandle s  = mDriver.openVideoStream( source, destFormat, syncedSink );
                if( s != null ) {
                    success = true;
                    mLock.unblock();
                    return s;
                }
            } finally {
                if( !success ) {
                    syncedSink.close();
                }
            }
                
            return null;
            
        }
    }
    
    
    public StreamHandle openAudioStream( StreamHandle source,
                                         AudioFormat format,
                                         Sink<? super AudioPacket> sink )
                                         throws IOException 
    {
        synchronized( mLock ) {
            boolean success = false;
            Sink syncedSink = mSyncer.openPipe( sink, mLock );
            
            try {
                StreamHandle s = mDriver.openAudioStream( source, format, syncedSink );
                if( s != null ) {
                    success = true;
                    mLock.unblock();
                    return s;
                }
            } finally {
                if( !success ) {
                    syncedSink.close();
                }
            }
            
            return null;
        }
    }
    
    
    public boolean closeStream( StreamHandle stream ) throws IOException {
        synchronized( mLock ) {
            boolean ret = mDriver.closeStream( stream );
            if( !ret ) {
                return false;
            }
            mLock.unblock();
            return true;
        }
    }
    
    
    public void close() {
        mDriver.close();
        mLock.interrupt();
    }
    
    
    public boolean isOpen() {
        return mDriver.isOpen();
    }
    
    
    private void runLoop() {
        boolean sendPacket = false;
        
        while( true ) {
            synchronized( mLock ) {
                if( !mDriver.hasNext() ) {
                    sendPacket = false;
                    
                    if( !mDriver.isOpen() ) {
                        sLog.fine( "Driver shutdown complete." );
                        return;
                    }
                    
                    try {
                        mLock.block();
                    } catch( InterruptedIOException ex ) {}
                    
                    continue;
                }
            }
            
            if( sendPacket ) {
                mDriver.sendCurrent();
                sendPacket = false;
            } else {
                sendPacket = mDriver.readPacket();
            }
        }
    }
    
    
    private final class PlayHandler implements PlayControl {

        public void playStart( long execMicros ) {}

        public void playStop( long execMicros ) {}

        public void seek( long execMicros, long seekMicros ) {
            synchronized( mLock ) {
                mDriver.seek( seekMicros );
                mLock.interrupt();
            }
        }

        public void setRate( long execMicros, double rate ) {}
        
    }
    
}
