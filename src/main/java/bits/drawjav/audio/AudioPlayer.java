///*
// * Copyright (c) 2015. Massachusetts Institute of Technology
// * Released under the BSD 2-Clause License
// * http://opensource.org/licenses/BSD-2-Clause
// */
//
//package bits.drawjav.audio;
//
//import bits.collect.RingList;
//import bits.drawjav.*;
//import bits.microtime.*;
//import bits.util.event.EventCaster;
//
//import java.io.IOException;
//import java.io.InterruptedIOException;
//import java.nio.channels.Channel;
//import java.util.*;
//
//
///**
// * @author Philip DeCamp
// */
//public class AudioPlayer implements Channel, SyncClockControl {
//
//    private static final int CACHE_MAX = 64;
//
//    final MemoryManager  mMem;
//    final PlayClock      mClock;
//
//    final ClockEventQueue mEvents     = new ClockEventQueue( 1024 );
//    final ClockState      mClockState;
//
//    final Pipe         mSource;
//    final AudioClipper mClipper;
//    final SolaPipe     mSola;
//    final PlayerPipe   mPlayer;
//
//    final Queue<AudioPacket> mInput = new RingList<AudioPacket>( CACHE_MAX );
//    final List<Node>         mNodes = new ArrayList<Node>();
//    final Stack<Node>        mStack = new Stack<Node>();
//
//    final EventCaster<SyncClockControl> mSyncNodes = EventCaster.create( SyncClockControl.class,
//                                                                         EventCaster.THREADING_SYNCHRONOUS );
//
//    private boolean mOpen         = true;
//    private boolean mHasUpdate    = true;
//    private boolean mHasException = false;
//
//
//    AudioPlayer( MemoryManager mem,
//                 AudioFormat format,
//                 PlayClock optClock,
//                 Pipe<AudioPacket> source )
//                 throws IOException
//    {
//        if( optClock == null ) {
//            optClock = new FullClock( Clock.SYSTEM_CLOCK );
//        }
//
//        mMem     = mem;
//        mClock   = optClock;
//        mClockState = new ClockState();
//        mClock.applyTo( mClockState );
//
//        mSource  = source;
//        mClipper = new AudioClipper( null );
//        mSola    = new SolaPipe( format.sampleRate(), null );
//        mPlayer  = new PlayerPipe();
//
//        Node sourceNode  = new Node( mSource );
//        Node clipperNode = new Node( mClipper );
//        Node solaNode    = new Node( mSola );
//        Node playerNode  = new Node( mPlayer );
//
//        //connect( sourceNode, clipperNode );
//        //connect( clipperNode, solaNode );
//        //connect( solaNode, playerNode );
//        connect( sourceNode, playerNode );
//
//        mSyncNodes.addListener( mClockState );
//        //mSyncNodes.addListener( mClipper );
//        //mSyncNodes.addListener( mSola );
//        mSyncNodes.addListener( mPlayer );
//
//        Thread t = new Thread( AudioPlayer.class.getSimpleName() ) {
//            @Override
//            public void run() {
//                runLoop();
//            }
//        };
//        t.start();
//    }
//
//
//    @Override
//    public synchronized void close() throws IOException {
//        if( !mOpen ) {
//            return;
//        }
//        mOpen = false;
//        mHasException = true;
//        notifyAll();
//    }
//
//    @Override
//    public synchronized boolean isOpen() {
//        return mOpen;
//    }
//
//    @Override
//    public synchronized void clockStart( long execMicros ) {
//        mEvents.clockStart( execMicros );
//        notifyAll();
//    }
//
//    @Override
//    public synchronized void clockStop( long execMicros ) {
//        mEvents.clockStop( execMicros );
//        notifyAll();
//    }
//
//    @Override
//    public synchronized void clockSeek( long execMicros, long seekMicros ) {
//        mEvents.clockSeek( execMicros, seekMicros );
//        notifyAll();
//    }
//
//    @Override
//    public synchronized void clockRate( long execMicros, Frac rate ) {
//        mEvents.clockRate( execMicros, rate );
//        notifyAll();
//    }
//
//
//
//    private void connect( Node parent, Node child ) {
//        if( !mNodes.contains( parent ) ) {
//            mNodes.add( parent );
//        }
//        if( !mNodes.contains( child ) ) {
//            mNodes.add( child );
//        }
//        parent.mSink = child;
//        child.mSource = parent;
//        mStack.clear();
//        notifyAll();
//    }
//
//
//    private void runLoop() {
//        while( true ) {
//            Node target;
//
//            synchronized( this ) {
//                if( mHasException ) {
//                    if( !mOpen ) {
//                        doClose();
//                        return;
//                    }
//                    doWaitForUpdate();
//                    continue;
//                }
//
//                ClockEvent event = mEvents.poll();
//                if( event != null ) {
//                    doProcessEvent( event );
//                    continue;
//                }
//
//                if( mStack.isEmpty() ) {
//                    for( Node n: mNodes ) {
//                        if( n.isSink() ) {
//                            mStack.push( n );
//                        }
//                    }
//                    if( mStack.isEmpty() ) {
//                        // Nothing to do.
//                        doWaitForUpdate();
//                        continue;
//                    }
//                }
//                target = mStack.peek();
//            }
//
//            while( target.mProcessed.isEmpty() && target.mSource != null ) {
//                target = target.mSource;
//                mStack.push( target );
//            }
//
//            if( target.mSource != null ) {
//
//            } else {
//
//            }
//
//            while( true ) {
//                if( target.mSource.mProcessed.isEmpty() ) {
//                    if( target.isSource() ) {
//                        try {
//                            Pipe.Result result = target.mPipe.process( null, target.mProcessed );
//                            if( result == Pipe.Result.NOTHING_TO_DO ) {
//                                doWaitForUpdate();
//                            } else {
//                                if( target.mProcessed.isEmpty() ) {
//
//                                }
//                            }
//                        } catch( InterruptedIOException ignore ) {
//                        } catch( IOException ex ) {
//                            ex.printStackTrace();
//                            mHasException = true;
//                        }
//                        break;
//                    }
//
//                    if( !target.mProcessed.isEmpty() ) {
//                        mStack.pop();
//                    }
//
//                    break;
//
//                } else if( target.mSource.mProcessed.isEmpty() ) {
//                    target = target.mSource;
//                    mStack.push( target );
//
//                } else {
//
//
//                }
//            }
//        }
//    }
//
//
//    private void doClose() {
//        for( Node n: mNodes ) {
//            try {
//                n.mPipe.close();
//            } catch( IOException ignore ) {}
//        }
//    }
//
//
//    private void doProcessEvent( ClockEvent event ) {
//        event.apply( mSyncNodes.cast() );
//
//        switch( event.mId ) {
//        case ClockEvent.CLOCK_RATE:
//            for( Node n: mNodes ) {
//                if( !n.isSink() ) {
//                    n.mPipe.clear();
//                }
//            }
//            mSyncNodes.cast().clockSeek( mClockState.mMasterBasis, mClockState.mTimeBasis );
//            break;
//
//        case ClockEvent.CLOCK_SEEK:
//            for( Node n: mNodes ) {
//                if( !n.isSink() ) {
//                    n.mPipe.clear();
//                }
//            }
//            break;
//        }
//    }
//
//
//    private synchronized void doWaitForUpdate() {
//        try {
//            wait();
//        } catch( InterruptedException ignore ) {}
//    }
//
//
//    private static final class Node {
//        Pipe mPipe;
//
//        Node mSource    = null;
//        Node mSink      = null;
//        List mProcessed = new ArrayList();
//
//
//        Node( Pipe pipe ) {
//            mPipe = pipe;
//        }
//
//
//        public boolean isSource() {
//            return mSource == null;
//
//        }
//
//
//        public boolean isSink() {
//            return mSink == null;
//        }
//
//    }
//
//}
