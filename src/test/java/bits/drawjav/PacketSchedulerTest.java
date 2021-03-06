/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.*;
import java.nio.channels.ClosedChannelException;
import java.util.*;

import bits.drawjav.old.PacketScheduler;
import bits.drawjav.old.Sink;
import bits.microtime.*;
import bits.util.concurrent.ThreadLock;
import bits.util.ref.*;


public class PacketSchedulerTest {
    
    
    public static void main( String[] args ) throws Exception {
        test1();
    }
    
    
    public static void test1() throws Exception {
        PlayController playCont  = PlayController.createAuto();
        ClockControl control     = playCont.control();
        LongPrinter printer      = new LongPrinter();
        PacketScheduler exec     = new PacketScheduler( playCont.clock() );
        List<LongSource> drivers = new ArrayList<LongSource>();
        
        for( int i = 0; i < 3; i++ ) {
            drivers.add( new LongSource( playCont, exec, printer ) );
        }
        
        control.clockStart();
        
        Thread.sleep( 2500L );
        control.clockSeek( 0 );
        
        Thread.sleep( 2500L );
        control.clockStop();
        Thread.sleep( 1000L );
        control.clockStart();
        Thread.sleep( 2000L );
        exec.close();
        
        while( true ) {
            Thread.sleep( 10000L );
        }
        
    }
        
    
    
    private static final class LongPrinter implements Sink<LongPacket> {
        
        public void consume( LongPacket p ) {
            System.out.format( "%s : %4.2f\n", 
                               p.mStream.toString(),
                               ( p.startMicros() / 1000000.0 ) );
        }
        
        public void clear() {
            System.out.println( "Clear" );
        }
        
        public void close() {
            System.out.println( "Close" );
        }
        
        public boolean isOpen() {
            return true;
        }
        
    }
    
    
    private static final class LongSource implements SyncClockControl, Closeable {

        private final PlayController         mPlayCont;
        private final Stream                 mStream;
        private final ObjectPool<LongPacket> mPool;
        private final Sink<LongPacket>       mSink;
        private final ThreadLock             mLock;

        private long    mMicros     = 0;
        private boolean mNeedSeek   = false;
        private long    mSeekMicros = 0;


        LongSource( PlayController playCont,
                    PacketScheduler exec,
                    Sink<LongPacket> sink )
                throws IOException
        {
            mPlayCont = playCont;
            mMicros = playCont.clock().micros();
            mStream = new BasicStream( new StreamFormat() );

            mPool = new HardPool<LongPacket>( 16 );
            mLock = new ThreadLock();
            mSink = exec.openPipe( sink, mLock, 4 );
            mPlayCont.clock().addListener( this );

            Thread thread = new Thread() {
                public void run() {
                    runLoop();
                }
            };

            thread.setDaemon( true );
            thread.start();
        }


        public void close() {
            try {
                mSink.close();
            } catch( Exception ex ) {
            }
        }


        private void runLoop() {
            boolean needClear = false;

            while( true ) {
                synchronized( mLock ) {
                    if( mNeedSeek ) {
                        mNeedSeek = false;
                        mLock.reset();
                        mMicros = mSeekMicros + 1000000L;
                        needClear = true;
                    }
                }

                if( needClear ) {
                    needClear = false;
                    mSink.clear();
                }

                LongPacket p = mPool.poll();
                if( p == null ) {
                    p = new LongPacket( mPool, mStream, 0 );
                }
                p.mMicros = mMicros;
                mMicros += 1000000L;

                try {
                    mSink.consume( p );
                } catch( InterruptedIOException ex ) {
                    //System.out.println( "Interrupted." );
                } catch( ClosedChannelException ex ) {
                    return;
                } catch( Exception ex ) {
                    ex.printStackTrace();
                }
                
                p.deref();
            }
        }

        
        @Override
        public void clockStart( long execMicros ) {}

        
        @Override
        public void clockStop( long execMicros ) {}

        
        @Override
        public void clockSeek( long execMicros, long gotoMicros ) {
            System.out.println( "SEEK" );
            synchronized( mLock ) {
                mNeedSeek = true;
                mSeekMicros = gotoMicros;
                mLock.interrupt();
            }
        }
       
        
        @Override
        public void clockRate( long execMicros, Frac rate ) {}
        
    }
    
    
    private static final class LongPacket extends AbstractRefable implements Packet {

        private final Stream mStream;
        long mMicros;

        public LongPacket( ObjectPool<LongPacket> pool,
                           Stream stream,
                           long micros )
        {
            super( pool );
            mStream = stream;
            mMicros = micros;
        }


        @Override
        public Stream stream() {
            return mStream;
        }

        @Override
        public long startMicros() {
            return mMicros;
        }

        @Override
        public long stopMicros() {
            return mMicros;
        }


        @Override
        protected void freeObject() {}

    }

}
