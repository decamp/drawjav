package cogmac.drawjav;

import java.io.*;
import java.nio.channels.ClosedChannelException;
import java.util.*;

import bits.clocks.*;
import bits.jav.JavConstants;
import bits.langx.ref.*;

public class PacketSchedulerTest {
    
    
    public static void main( String[] args ) throws Exception {
        test1();
    }
    
    
    
    public static void test1() throws Exception {
        PlayController playCont  = PlayController.newAutoInstance();
        AsyncPlayControl control = playCont.control();
        LongPrinter printer      = new LongPrinter();
        PacketScheduler exec     = new PacketScheduler( playCont );
        List<LongSource> drivers = new ArrayList<LongSource>();
        
        for( int i = 0; i < 3; i++ ) {
            drivers.add( new LongSource( playCont, exec, printer ) );
        }
        
        control.playStart();
        
        Thread.sleep( 2500L );
        control.seek( 0 );
        
        Thread.sleep( 2500L );
        control.playStop();
        Thread.sleep( 1000L );
        control.playStart();
        Thread.sleep( 2000L );
        exec.close();
        
        while( true ) {
            Thread.sleep( 10000L );
        }
        
    }
        
    
    
    private static final class LongPrinter implements Sink<LongPacket> {
        
        public void consume( LongPacket p ) {
            System.out.format( "%s : %4.2f\n", 
                               p.mStream.guid().toString(),
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
    
    
    
    private static final class LongSource implements PlayControl {
        
        private final PlayController mPlayCont;
        private final StreamHandle mStream;
        private final RefPool<LongPacket> mPool;
        private final Sink<LongPacket> mSink;
        private final ThreadLock mLock;
        
        private long mMicros = 0;
        private boolean mNeedSeek = false;
        private long mSeekMicros  = 0;
        
        
        LongSource( PlayController playCont,
                    PacketScheduler exec,
                    Sink<LongPacket> sink )
                    throws IOException
        {
            mPlayCont = playCont;
            mMicros   = playCont.clock().micros();
            mStream  = new BasicStreamHandle( JavConstants.AVMEDIA_TYPE_UNKNOWN, 
                                              null, 
                                              null );
            
            mPool = new HardRefPool<LongPacket>( 16 );
            mLock = new ThreadLock();
            mSink = exec.openPipe( sink, mLock, 4 );
            mPlayCont.caster().addListener( this );
            
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
            } catch( Exception ex ) {}
        }
        
        
        private void runLoop() {
            int count = 0;
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
        public void playStart( long execMicros ) {}

        
        @Override
        public void playStop( long execMicros ) {}

        
        
        @Override
        public void seek( long execMicros, long gotoMicros ) {
            System.out.println( "SEEK" );
            synchronized( mLock ) {
                mNeedSeek = true;
                mSeekMicros = gotoMicros;
                mLock.interrupt();
            }
        }


        
        
        @Override
        public void setRate( long execMicros, double rate ) {}
        
        
        
    }
    
    
    
    private static final class LongPacket extends AbstractRefable implements Packet {
        
        private final StreamHandle mStream;
        long mMicros;
        
        public LongPacket( RefPool<LongPacket> pool,
                           StreamHandle stream,
                           long micros ) 
        {
            super( pool );
            mStream = stream;
            mMicros = micros;
            //System.out.println( "alloc: PACKET" );
        }
        
        
        @Override
        public StreamHandle stream() {
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
