/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.*;
import java.util.logging.*;

import bits.drawjav.audio.AudioFormat;
import bits.drawjav.audio.AudioPacket;
import bits.drawjav.video.PictureFormat;
import bits.drawjav.video.VideoPacket;
import bits.microtime.*;
import bits.util.concurrent.ThreadLock;


/**
 * Handles realtime processing of one source.
 * 
 * @author decamp
 */
@SuppressWarnings( { "unchecked", "rawtypes" } )
public class RealtimeDriver implements StreamDriver {
    
    private static Logger sLog = Logger.getLogger( RealtimeDriver.class.getName() );

    private final PlayController  mPlayCont;
    private final PassiveDriver   mDriver;
    private final PacketScheduler mSyncer;
    private final PlayHandler     mPlayHandler;
    private final ThreadLock      mLock;
    private final Thread          mThread;


    public RealtimeDriver( PlayController playCont, Source source, PacketScheduler optSyncer ) {
        mPlayCont = playCont;
        mDriver = new PassiveDriver( source );
        mSyncer = optSyncer != null ? optSyncer : new PacketScheduler( playCont );
        mPlayHandler = new PlayHandler();
        mLock = new ThreadLock();
        mPlayCont.clock().addListener( mPlayHandler );

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
    
    
    public StreamHandle openVideoStream( Source source,
                                         StreamHandle stream,
                                         PictureFormat destFormat,
                                         Sink<? super VideoPacket> sink )
                                         throws IOException 
    {
        return openStream( true, source, stream, destFormat, null, sink );
    }
    
    
    public StreamHandle openAudioStream( Source source,
                                         StreamHandle stream,
                                         AudioFormat format,
                                         Sink<? super AudioPacket> sink )
                                         throws IOException 
    {
        return openStream( false, source, stream, null, format, sink );
    }


    public StreamHandle openStream( boolean isVideo,
                                    Source source,
                                    StreamHandle stream,
                                    PictureFormat pictureFormat,
                                    AudioFormat audioFormat,
                                    Sink sink )
                                    throws IOException
    {
        synchronized( mLock ) {
            mSyncer.openPipe( sink, mLock );
            Sink syncedSink = mSyncer.openPipe( sink, mLock );
            StreamHandle ret;

            boolean abort = true;
            try {
                if( isVideo ) {
                    ret = mDriver.openVideoStream( source, stream, pictureFormat, syncedSink );
                } else {
                    ret = mDriver.openAudioStream( source, stream, audioFormat, syncedSink );
                }
                if( ret == null ) {
                    return null;
                }
                abort = false;
            } finally {
                if( abort ) {
                    syncedSink.close();
                }
            }


            mLock.unblock();
            return ret;
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
                    } catch( InterruptedIOException ignore ) {}
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
    
    
    private final class PlayHandler implements SyncClockControl {

        public void clockStart( long execMicros ) {}

        public void clockStop( long execMicros ) {}

        public void clockSeek( long execMicros, long seekMicros ) {
            synchronized( mLock ) {
                mDriver.seek( seekMicros );
                mLock.interrupt();
            }
        }

        public void clockRate( long execMicros, Frac rate ) {}
        
    }
    
}
