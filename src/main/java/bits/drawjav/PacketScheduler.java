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

import bits.microtime.*;
import bits.util.concurrent.ThreadLock;
import bits.util.ref.*;


/**
 * Schedules sending of packet streams.
 * 
 * @author decamp
 */
@SuppressWarnings( { "unchecked", "rawtypes" } )
public class PacketScheduler {
    
    private static final int COMMAND_CLOSE   = 0; // In order of priority.
    private static final int COMMAND_CLEAR   = 1;
    private static final int COMMAND_CONSUME = 2;
    
    private static final boolean RUSH_FIRST_VIDEO_FRAME = true;
    
    private static final Logger sLog = Logger.getLogger( PacketScheduler.class.getName() );
    
    private final PlayClock mClock;
    private final EventHandler mEventHandler;
    private final TimedMultiQueue<Command> mQueue;
    
    private boolean mClosed   = false;
    private List<Pipe> mPipes = new ArrayList<Pipe>();
    
    private int mDefaultQueueCap = 8;
    
    
    public PacketScheduler( PlayClock clock ) {
        mClock = clock;
        mQueue = new TimedMultiQueue<Command>();
        
        Thread thread = new Thread( PacketScheduler.class.getName() ) {
            public void run() {
                runLoop();
            }
        };
        
        thread.setPriority( Thread.NORM_PRIORITY );
        thread.setDaemon( true );
        thread.start();
        
        mEventHandler = new EventHandler( mQueue, clock );
    }
    
    
    
    public PlayClock clock() {
        return mClock;
    }
    
    
    public int defaultQueueCapacity() {
        return mDefaultQueueCap;
    }
    
    
    public void defaultQueueCapacity( int queueCap ) {
        mDefaultQueueCap = queueCap;
    }
    
    
    public void close() {
        List<Pipe> pipes = null;
        synchronized( this ) {
            if( mClosed ) {
                return;
            }
            mClosed = true;
            pipes   = mPipes;
            mPipes  = null;
        }
        mEventHandler.close();
        for( Pipe p: pipes ) {
            p.close();
        }
        mQueue.close();
    }
    
    
    public <T extends Packet> Sink<T> openPipe( Sink<? super T> sink ) throws IOException {
        return openPipe( sink, null, mDefaultQueueCap );
    }
    
    
    public <T extends Packet> Sink<T> openPipe( Sink<? super T> sink, 
                                                ThreadLock lock )
                                                throws IOException 
    {
        return openPipe( sink, lock, mDefaultQueueCap );
    }
    
        
    public <T extends Packet> Sink<T> openPipe( Sink<? super T> sink, 
                                                ThreadLock lock, 
                                                int queueCapacity )
                                                throws IOException 
    {
        synchronized( this ) {
            if( mClosed ) {
                throw new ClosedChannelException();
            }
            if( lock == null ) {
                lock = new ThreadLock();
            }
            Pipe pipe = new Pipe( mClock,
                                  mQueue,
                                  sink, 
                                  lock,
                                  queueCapacity,
                                  RUSH_FIRST_VIDEO_FRAME );
            
            mPipes.add( pipe );
            return pipe;
        }
    }
        
    
    
    private void runLoop() {
        while( true ) {
            Command c = mQueue.remove();
            if( c == null ) {
                sLog.log( Level.FINE, "PacketScheduler shutdown complete." );
                return;
            }
            
            // Execute command.
            try {
                switch( c.mCommandCode ) {
                case COMMAND_CONSUME:
                    c.mPipe.mSink.consume( c.mPacket );
                    break;
            
                case COMMAND_CLEAR:
                    c.mPipe.mSink.clear();
                    break;
                
                case COMMAND_CLOSE:
                    synchronized( this ) {
                        if( mPipes != null ) {
                            mPipes.remove( c.mPipe );
                        }
                    }
                    c.mPipe.mSink.close();
                    break;
                    
                default:
                    warn( "Unknown command scheduled by PacketScheduler.", null );
                }
            } catch( InterruptedIOException ignore ) {
            } catch( ClosedChannelException ex ) {
                c.mPipe.close();
            } catch( Exception ex ) {
                warn( "Exception in pipeline.", ex );
                c.mPipe.close();
            } finally {
                c.deref();
            }
        }
    }

    
    private static void warn( String msg, Exception ex ) {
        sLog.log( Level.WARNING, msg, ex );
    }
        
    
    private static final class Pipe extends DoubleLinkedNode implements Sink, ObjectPool<Command> {

        @SuppressWarnings( "unused" )
        private final PlayClock mClock;
        private final TimedMultiQueue mQueue;
        private final Object mChannel;
        private final Sink mSink;
        private final ThreadLock mLock;
        
        private boolean mClosed = false;

        private final int mCap;
        private int mQueueSize = 0;

        private final int mPoolCap;
        private Command mPool = null;
        private int mPoolSize = 0;

        private final boolean mRushFirstVideoFrame;
        private boolean mNeedRush;


        Pipe( PlayClock clock,
              TimedMultiQueue queue,
              Sink sink, 
              ThreadLock lock, 
              int queueCap, 
              boolean rushFirstVideoFrame )
              throws ClosedChannelException
        {
            mClock   = clock;
            mQueue   = queue;
            mChannel = queue.openChannel();
            mSink    = sink;
            mLock    = lock;
            mCap     = queueCap;
            mPoolCap = Math.max( 128, Math.min( queueCap, 1024 ) );
            mRushFirstVideoFrame = rushFirstVideoFrame;
            mNeedRush            = rushFirstVideoFrame;
        }
        
        
        
        public void consume( Object packet ) throws IOException {
            synchronized( mLock ) {
                while( mQueueSize >= mCap && !mClosed ) {
                    mLock.block();
                }
                if( mClosed ) {
                    throw new ClosedChannelException();
                }
                
                Packet p = (Packet)packet;
                p.ref();
                
                Command command      = poll();
                command.mCommandCode = COMMAND_CONSUME;
                command.mPriority    = COMMAND_CONSUME;
                command.mPacket      = p;
                
                // If cleared, get video packet out as soon as possible.
                if( mNeedRush && ( p instanceof DrawPacket) ) {
                    mNeedRush = false;
                    command.mDataMicros = Long.MIN_VALUE;
                } else {
                    command.mDataMicros = p.startMicros();
                }
                
                try {
                    mQueue.offer( mChannel, command );
                } catch( ClosedChannelException ex ) {
                    mClosed = true;
                } finally {
                    command.deref();
                }
            }
        }
        
        
        public void clear() {
            synchronized( mLock ) {
                if( mClosed ) {
                    return;
                }

                //TODO: Check if this should be:
                mNeedRush = mRushFirstVideoFrame;
//                mNeedRush = true;
                Command command = poll();
                command.mCommandCode = COMMAND_CLEAR;
                command.mPriority    = COMMAND_CLEAR;
                command.mDataMicros  = Long.MIN_VALUE;
                mQueue.clearChannel( mChannel );
                
                try {
                    mQueue.offer( mChannel, command );
                } catch( ClosedChannelException ex ) {
                    mClosed = true;
                } finally {
                    command.deref();
                }
            }
        }
        
        
        public void close() {
            synchronized( mLock ) {
                if( mClosed ) {
                    return;
                }
                mClosed = true;
                Command command = null;
                
                try {
                    mQueue.clearChannel( mChannel );
                    command = poll();
                    command.mCommandCode = COMMAND_CLOSE;
                    command.mPriority    = COMMAND_CLOSE;
                    command.mDataMicros  = Long.MIN_VALUE;
                    mQueue.offer( mChannel, command );
                } catch( ClosedChannelException ignore ) {
                } finally {
                    if( command != null ) {
                        command.deref();
                    }
                }
            }
        }
        
        
        public boolean isOpen() {
            return !mClosed;
        }
        
        
        public boolean offer( Command c ) {
            synchronized( mLock ) {
                if( !mClosed ) {
                    if( mQueueSize-- >= mCap ) {
                        mLock.unblock();
                    }
                    if( mQueueSize < 0 ) {
                        mQueueSize = 0;
                    }
                    
                    if( mPoolSize < mPoolCap ) {
                        mPoolSize++;
                        c.mPoolNext = mPool;
                        mPool = c;
                    }
                }
                
                return true;
            }
        }
        
        
        public Command poll() {
            mQueueSize++;
            
            if( mPool == null ) {
                mPoolSize = 0;
                return new Command( this, mClock );
            }

            Command c = mPool;
            mPool = c.mPoolNext;
            mPoolSize--;
            return c;
        }
        
    }
    
    
    private static final class EventHandler implements SyncClockControl {

        private final TimedMultiQueue mQueue;
        private final PlayClock       mClock;


        public EventHandler( TimedMultiQueue core, PlayClock clock ) {
            mQueue = core;
            mClock = clock;
            clock.addListener( this );
        }


        public void close() {
            mClock.removeListener( this );
        }

        @Override
        public void clockStart( long execMicros ) {
            mQueue.wakeup();
        }

        @Override
        public void clockStop( long execMicros ) {
            mQueue.wakeup();
        }

        @Override
        public void clockSeek( long execMicros, long gotoMicros ) {
            mQueue.wakeup();
        }

        @Override
        public void clockRate( long execMicros, Frac rate ) {
            mQueue.wakeup();
        }

    }


    private static final class Command extends TimedNode {

        public final Pipe      mPipe;
        public final PlayClock mClock;

        public int    mCommandCode;
        public int    mPriority;
        public Packet mPacket;
        public long   mDataMicros;

        private Command mPoolNext = null;
        private int     mRefCount = 1;


        public Command( Pipe parent, PlayClock clock ) {
            //System.out.println( "createAuto: COMMAND" );
            mPipe = parent;
            mClock = clock;
        }


        public boolean ref() {
            synchronized( mPipe.mLock ) {
                mRefCount++;
                return true;
            }
        }


        public void deref() {
            synchronized( mPipe.mLock ) {
                if( --mRefCount > 0 ) {
                    return;
                }

                mRefCount = 1;

                if( mPacket != null ) {
                    mPacket.deref();
                    mPacket = null;
                }

                mPipe.offer( this );
            }
        }


        public int refCount() {
            return mRefCount;
        }


        public int compareTo( TimedNode t ) {
            Command b = (Command)t;
            int c = mPriority - b.mPriority;
            return c != 0 ? c :
                    mDataMicros < b.mDataMicros ? -1 :
                            mDataMicros > b.mDataMicros ? 1 : 0;
        }


        public long presentationMicros() {
            if( mDataMicros == Long.MIN_VALUE ) {
                return mDataMicros;
            }
            long t = mClock.toMaster( mDataMicros );
            if( t == Long.MIN_VALUE || t == Long.MAX_VALUE ) {
                return t;
            }

            return t - mClock.masterMicros() + System.currentTimeMillis() * 1000L;
        }

    }

}


    