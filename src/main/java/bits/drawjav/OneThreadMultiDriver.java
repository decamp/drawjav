/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.*;
import java.nio.channels.ClosedChannelException;
import java.util.*;
import java.util.logging.*;

import bits.drawjav.audio.AudioFormat;
import bits.drawjav.audio.AudioPacket;
import bits.drawjav.video.PictureFormat;
import bits.drawjav.video.VideoPacket;
import bits.microtime.*;
import bits.util.concurrent.ThreadLock;


/**
 * @author decamp
 */
@SuppressWarnings( { "unchecked", "rawtypes" } )
public class OneThreadMultiDriver implements StreamDriver {

    private static Logger sLog = Logger.getLogger( OneThreadMultiDriver.class.getName() );


    private final PlayController  mPlayCont;
    private final ThreadLock      mLock;
    private final PacketScheduler mScheduler;
    private final PlayHandler     mPlayHandler;

    private final Map<Source, Node>       mSourceMap = new HashMap<Source, Node>();
    private final Map<StreamHandle, Node> mStreamMap = new HashMap<StreamHandle, Node>();
    private final PrioHeap<Node>          mDrivers   = new PrioHeap<Node>();

    private Thread mThread;

    private long mSeekWarmupMicros = 2000000L;
    private int  mVideoQueueCap    = 8;
    private int  mAudioQueueCap    = 16;

    private boolean mClosing = false;
    private boolean mCloseComplete = false;


    public OneThreadMultiDriver( PlayController playCont, PacketScheduler optSyncer ) {
        mPlayCont = playCont;
        mLock = new ThreadLock();
        mScheduler = optSyncer != null ? optSyncer : new PacketScheduler( playCont );

        mPlayHandler = new PlayHandler();
        mPlayCont.clock().addListener( mPlayHandler );

        mThread = new Thread( OneThreadMultiDriver.class.getSimpleName() ) {
            public void run() {
                runLoop();
            }
        };

        mThread.setDaemon( true );
        mThread.setPriority( Thread.NORM_PRIORITY - 1 );
    }


    public void start() {
        mThread.start();
    }


    public PlayController playController() {
        return mPlayCont;
    }


    public boolean isOpen() {
        return !mClosing;
    }


    public void close() {
        synchronized( mLock ) {
            if( mClosing ) {
                return;
            }
            mClosing = true;
            mStreamMap.clear();
            mSourceMap.clear();
            mLock.interrupt();

            while( !mDrivers.isEmpty() ) {
                Node node = mDrivers.remove( 0 );
                node.mDriver.close();
            }
        }
    }


    public StreamHandle openVideoStream( Source source,
                                         StreamHandle sourceStream,
                                         PictureFormat destFormat,
                                         Sink<? super VideoPacket> sink )
                                         throws IOException 
    {
        return openStream( true, source, sourceStream, destFormat, null, sink );
    }
                              

    public StreamHandle openAudioStream( Source source,
                                         StreamHandle sourceStream,
                                         AudioFormat destFormat,
                                         Sink<? super AudioPacket> sink )
                                         throws IOException 
    {
        return openStream( false, source, sourceStream, null, destFormat, sink );
    }


    private StreamHandle openStream( boolean isVideo,
                                     Source source,
                                     StreamHandle stream,
                                     PictureFormat pictureFormat,
                                     AudioFormat audioFormat,
                                     Sink sink )
                                     throws IOException
    {
        synchronized( mLock ) {
            if( mClosing ) {
                throw new ClosedChannelException();
            }

            Node node = mSourceMap.get( source );
            boolean newNode = false;
            if( node == null ) {
                newNode = true;
                PassiveDriver driver = new PassiveDriver( source );
                driver.seekWarmupMicros( mSeekWarmupMicros );
                node = new Node( source, driver );
            }

            Sink syncSink = mScheduler.openPipe( sink, mLock, mVideoQueueCap );
            StreamHandle ret;
            boolean abort = true;

            try {
                if( isVideo ) {
                    ret = node.mDriver.openVideoStream( source, stream, pictureFormat, syncSink );
                } else {
                    ret = node.mDriver.openAudioStream( source, stream, audioFormat, syncSink );
                }

                if( ret == null ) {
                    return null;
                }
                abort = false;
            } finally {
                if( abort ) {
                    syncSink.close();
                    if( newNode ) {
                        node.mDriver.close();
                    }
                }
            }

            if( newNode ) {
                mSourceMap.put( source, node );
                mDrivers.offer( node );
            } else {
                mDrivers.reschedule( node );
            }
            mStreamMap.put( ret, node );
            mLock.unblock();
            return ret;
        }
    }

    
    public boolean closeStream( StreamHandle stream ) throws IOException {
        synchronized( mLock ) {
            Node node = mStreamMap.remove( stream );
            if( node == null ) {
                return false;
            }
            boolean ret = node.mDriver.closeStream( stream );
            if( !node.mDriver.hasSink() ) {
                mSourceMap.remove( node.mSource );
                mDrivers.remove( node );
                node.mDriver.close();
            } else {
                mDrivers.reschedule( node );
            }

            mLock.unblock();
            return ret;
        }
    }


    
    private void runLoop() {
        Node s = null;
        boolean sendPacket = false;
        
        while( true ) {
            synchronized( mLock ) {
                if( s != null ) {
                    mDrivers.reschedule( s );
                }
                
                s = mDrivers.peek();
                if( s == null ) {
                    // Nothing to do.
                    if( mClosing ) {
                        sLog.fine( "Driver shutdown complete." );
                        mCloseComplete = true;
                        return;
                    }
                    
                    try {
                        mLock.block();
                    } catch( InterruptedIOException ex ) {}
                    
                    continue;
                }
                
                if( !s.mDriver.hasNext() ) {
                    if( !s.mDriver.isOpen() ) {
                        mDrivers.remove();
                        continue;
                    }
                    
                    try {
                        mLock.block();
                    } catch( InterruptedIOException ex ) {}
                    
                    continue;
                }
                
                sendPacket = s.mDriver.hasCurrent();
            }
            
            if( sendPacket ) {
                s.mDriver.sendCurrent();
            } else {
                s.mDriver.readPacket();
            }
        }
    }



    private final class PlayHandler implements SyncClockControl {
        
        public void clockStart( long execMicros ) {}

        public void clockStop( long execMicros ) {}

        public void clockSeek( long execMicros, long seekMicros ) {
            synchronized( mLock ) {
                int len = mDrivers.size();
                for( int i = 0; i < len; i++ ) {
                    mDrivers.get( i ).mDriver.seek( seekMicros );
                }
                mLock.interrupt();
            }
        }

        public void clockRate( long execMicros, Frac rate ) {}
    }
    
    
    private static final class Node extends HeapNode implements Comparable<Node> {

        final Source mSource;
        final PassiveDriver mDriver;

        Node( Source source, PassiveDriver driver  ) {
            mSource = source;
            mDriver = driver;
        }


        public int compareTo( Node s ) {
            boolean r0 = mDriver.hasNext();
            boolean r1 = s.mDriver.hasNext();

            if( r0 && r1 ) {
                // Both are readable. Sort based on next packet.
                long t0 = mDriver.currentMicros();
                long t1 = s.mDriver.currentMicros();
                return t0 < t1 ? -1 :
                        t0 > t1 ? 1 : 0;
            }

            // Schedule closed drivers first.
            boolean close0 = !mDriver.isOpen();
            boolean close1 = !s.mDriver.isOpen();

            if( close0 ) {
                return close1 ? 0 : -1;
            } else if( close1 ) {
                return 1;
            }

            return r0 ? -1 : (r1 ? 1 : 0);
        }

    }


}
