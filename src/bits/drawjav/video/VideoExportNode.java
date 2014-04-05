    package bits.drawjav.video;

import java.io.*;
import java.nio.*;
import java.util.*;

import javax.media.opengl.*;
import static javax.media.opengl.GL.*;

import bits.collect.RingList;
import bits.draw3d.nodes.DrawNode;
import bits.jav.util.Rational;
import bits.microtime.*;
import bits.util.Files;
import bits.util.ref.*;


/**
 * Draw node that encodes frame buffer directly to H264/MP4 file. 
 * Encoding is performed on separate thread for better performance.
 * Multiple video captures may be run in parallel.
 * Video captures may be scheduled at any time.
 * Video captures may be closed at any time using the Closeable handle provided by <code>addColorWriter</code>.
 * Video captures are closed automatically and safely on system exit events.
 * 
 * @author decamp
 */
public class VideoExportNode implements DrawNode {
    
    public static final int QUALITY_HIGHEST = 100;
    public static final int QUALITY_LOWEST  = 0;
    public static final int QUALITY_DEFAULT = 30;
    
    private static final int OVERHEAD  = 1024;
    private static final int ROW_ALIGN = 4;
    
    
    public static VideoExportNode newInstance() {
        return null;
    }
    
    
    private static final int MAX_QUEUE_SIZE = 2;
    
    private final Clock mClock;
    private final PriorityQueue<Stream> mNewStreams = new PriorityQueue<Stream>();
    private final List<Stream> mStreams = new ArrayList<Stream>();
    private final FlushThread mFlusher  = new FlushThread();
    
    private Integer mReadTarget     = null;
    private boolean mDoubleBuffered = false;
    
    private int mWidth  = 0;
    private int mHeight = 0;
    
    
    public VideoExportNode( Clock clock ) {
        mClock = clock;
    }
    
    
    /**
     * @param readTarget Specifies buffer to read data from. Must be GL_FRONT, GL_BACK, <code>null</code>.
     *                   If <code>null</code>, a buffer will be selected automatically.
     */
    public void readTarget( Integer readTarget ) {
        mReadTarget = readTarget;
    }
    
    /**
     * Adds video capture. The video capture will terminate upon one of three events: <br/>
     * 1. The internal clock reaches or exceeds the provided <code>stopMicros</code> param. <br/>
     * 2. <code>close()</code> is called on the <code>java.io.Closeable</code> object returned by this method. <br/>
     * 3. System shutdown, in which case a shutdown hook will attemp to terminate the video capture safely. <br/>
     * <p>
     * Encodings may have constant quality or constant bitrate, specified by the <code>quality</code> and
     * <code>bitrate</code> parameters: <br/>
     * quality &ge; 0 : Constant quality. <br/>
     * quality &lt; 0, bitrate &ge; 0 : Constant bitrate. <br/>
     * quality &lt; 0, bitrate &lt; 0 : Constant quality of 30.
     *
     * @param outFile     File to write video to. IF outFile.exists(), the existing file will not
     *                    be modified in any way, and a unique number will be added to the 
     *                    filename used.
     * @param quality     Quality of encoding.  0 = highest, 100 = lowest. Negative = use constant bitrate.
     *                    Default is 30 if both <code>quality</code> and <code>bitrate</code> parameters are negative.
     * @param bitrate     Bit rate of encoding. Only used if quality is negative.
     * @param startMicros When video catpure should begin. Use Long.MIN_VALUE to begin immediately.
     * @param stopMicros  When video capture should end. Use Long.MAX_VALUE to capture without set duration.
     * @return object that may be closed (<code>object.close()</code>) to end video capture. 
     * 
     */
    public Job addColorWriter( File outFile,
                               int quality,
                               int bitrate,
                               long startMicros,
                               long stopMicros )
                               throws IOException
    {
        outFile = Files.setSuffix( outFile, "mp4" );
        
        File outDir = outFile.getParentFile();
        if( !outDir.exists() && !outDir.mkdirs() ) {
            throw new IOException( "Failed to create dir: " + outDir.getPath() );
        }

        if( outFile.exists() ) {
            int count = 0;
            String name = Files.baseName( outFile );
            do {
                outFile = new File( outDir, String.format( "%s-%d.mp4", name, count++ ) );
            } while( outFile.exists() );
            if( !outFile.createNewFile() ) {
                throw new IOException();
            }
        }
        
        ObjectPool<ByteBuffer> pool = new HardPool<ByteBuffer>( MAX_QUEUE_SIZE + 1 );
        ColorReader reader = new ColorReader( pool );
        ColorWriter writer = new ColorWriter( outFile, quality, bitrate, 24, null, pool, mFlusher );
        Stream stream      = new Stream( startMicros, stopMicros, reader, writer );
        mNewStreams.offer( stream );
        
        return stream;
    }    
    
    
    
    @Override
    public void init( GLAutoDrawable glad ) {
        mDoubleBuffered = glad.getChosenGLCapabilities().getDoubleBuffered();
    }
    
    
    @Override
    public void reshape( GLAutoDrawable gld, int x, int y, int w, int h ) {
        mWidth  = w;
        mHeight = h;
    }

    
    @Override
    public void pushDraw( GL gl ) {}
    
    
    @Override
    public void popDraw( GL gl ) {
        long t = mClock.micros();
        
        while( !mNewStreams.isEmpty() && mNewStreams.peek().startMicros() <= t ) {
            Stream s = mNewStreams.remove();
            mStreams.add( s );
        }
        
        int len = mStreams.size();
        for( int i = 0; i < len; i++ ) {
            Stream s = mStreams.get( i );
            if( s.stopMicros() <= t || !s.process( gl, mWidth, mHeight ) ) {
                s.close();
                mStreams.remove( i-- );
                len--;
            }
        }
    }
    
    
    @Override
    public void dispose( GLAutoDrawable gld ) {
        for( Stream s: mStreams ) {
            s.close();
        }
        mStreams.clear();
        for( Stream s: mNewStreams ) {
            s.close();
        }
        mNewStreams.clear();
    }
    

    public static interface Job extends Closeable, TimeRanged {
        public void registerCompletionCallback( Runnable r );
    }
    
    
    private static interface Joinable extends Closeable {
        public void join() throws InterruptedException;
    }
    
    
    private static interface FrameReader {
        public ByteBuffer readFrame( GL gl, int w, int h );
    }
    
    
    private static interface FrameWriter extends Joinable {
        public boolean offer( ByteBuffer src, int w, int h ) throws IOException;
        public void close() throws IOException;
    }

    
    private final class ColorReader implements FrameReader {
        
        private final ObjectPool<ByteBuffer> mPool;
        
        public ColorReader( ObjectPool<ByteBuffer> pool ) {
            mPool = pool;
        }
        
        public ByteBuffer readFrame( GL gl, int w, int h ) {
            int rowBytes = ( w * 3 + ROW_ALIGN - 1 ) / ROW_ALIGN * ROW_ALIGN;
            int cap = rowBytes * h + OVERHEAD;
            
            ByteBuffer buf = mPool.poll();
            if( buf == null || buf.capacity() < cap ) {
                buf = ByteBuffer.allocateDirect( cap );
            } else {
                buf.clear();
            }
            
            buf.order( ByteOrder.nativeOrder() );
            
            if( mReadTarget != null ) {
                gl.glReadBuffer( mReadTarget );
            } else {
                gl.glReadBuffer( mDoubleBuffered ? GL_BACK : GL_FRONT );
            }
            
            gl.glPixelStorei( GL_PACK_ALIGNMENT, ROW_ALIGN );
            gl.glReadPixels( 0, 0, w, h, GL_BGR, GL_UNSIGNED_BYTE, buf );
            buf.position( 0 ).limit( rowBytes * h );
            return buf;
        }
        
    }
    
    
    private static final class ColorWriter implements FrameWriter, Runnable {
        
        private final File mOutFile;
        private final Mp4Writer mOut = new Mp4Writer();
        private final Queue<ByteBuffer> mQueue = new RingList<ByteBuffer>( 4 );
        private final ObjectPool<ByteBuffer> mPool;
        private final FlushThread mFlusher;
        
        private ByteBuffer mFlipBuf = null;
        private int mRowSize;
        private int mHeight;
        private Thread mThread = null;
        private boolean mClosed = false;
        
        
        public ColorWriter( File outFile, 
                            int quality,
                            int bitrate,
                            int gopSize,
                            Rational optTimeBase,
                            ObjectPool<ByteBuffer> pool,
                            FlushThread flusher )
        {
            mOutFile    = outFile;
            if( bitrate >= 0 ) {
                mOut.bitrate( bitrate );
            } else if( quality >= 0 ) {
                mOut.quality( quality );
            } else {
                mOut.quality( QUALITY_DEFAULT );
            }

            mOut.gopSize( gopSize );
            if( optTimeBase != null ) {
                mOut.timeBase( optTimeBase );
            }
            mPool = pool;
            mFlusher = flusher;
        }
    
        
        public synchronized boolean offer( ByteBuffer buf, int w, int h ) throws IOException {
            if( mThread == null ) {
                if( mClosed ) {
                    return false;
                }

                mHeight  = h;
                mRowSize = ( w * 3 + ROW_ALIGN - 1 ) / w * w;
                mOut.size( w, h );
                mOut.open( mOutFile );
                mThread = new Thread( this );
                mThread.setName( "Video Encoder" );
                mThread.setDaemon( true );
                mFlusher.add( this );
                mThread.start();
            }

            // Wait for opening.
            while( mQueue.size() >= MAX_QUEUE_SIZE ) {
                if( mClosed ) {
                    return false;
                }
                try {
                    wait();
                } catch( InterruptedException ignored ) {}
            }
            
            mQueue.offer( buf );
            notifyAll();
            return true;
        }
        
        
        public synchronized void close() {
            if( mClosed ) {
                return;
            }
            mClosed = true;
            notifyAll();
        }
        
        
        public void join() throws InterruptedException {
            Thread t = mThread;
            if( t == null ) {
                return;
            }
            t.join();
        }
        
        
        public void run() {
            try {
                while( process() ) {}
            } catch( IOException ex ) {
                ex.printStackTrace();
                synchronized( this ) {
                    mClosed = true;
                    notifyAll();
                }
            } finally {
                mFlusher.remove( this );
            }
        }
        
        
        private boolean process() throws IOException {
            ByteBuffer buf = null;
            int h;
            int stride;
            
            synchronized( this ) {
                if( !mQueue.isEmpty() ) {
                    buf = mQueue.remove();
                    notifyAll();
                } else if( !mClosed ) {
                    try {
                        wait(); 
                    } catch( InterruptedException ignored ) {}
                    return true;
                }
                
                h = mHeight;
                stride = mRowSize;
            }
            
            ByteBuffer flip = mFlipBuf;
            if( flip == null || flip.capacity() < h * stride ) {
                flip = mFlipBuf = ByteBuffer.allocateDirect( h * stride );
            }
            
            if( buf != null ) {
                flip.clear().limit( h*stride );

                for( int y = 0; y < h; y++ ) {
                    buf.limit( buf.position() + stride );
                    flip.position( ( h - y - 1 ) * stride );
                    flip.put( buf );
                }
                
                mOut.write( flip, stride );
                if( mPool != null ) {
                    mPool.offer( buf );
                }
                return true;
            }
            
            mOut.close();
            return false;
        }
    }
    
    
    private static final class Stream implements Comparable<Stream>, Job { 
        
        private final long mStartMicros;
        private final long mStopMicros;
        
        private final FrameReader mReader;
        private final FrameWriter mWriter;
        
        private final List<Runnable> mCompletionCallbacks = new ArrayList<Runnable>( 1 ); 

        
        Stream( long startMicros, 
                long stopMicros, 
                FrameReader reader, 
                FrameWriter writer ) 
        {
            mStartMicros = startMicros;
            mStopMicros  = stopMicros;
            mReader      = reader;
            mWriter      = writer;
        }
        
        
        
        public long startMicros() {
            return mStartMicros;
        }
        
        
        public long stopMicros() {
            return mStopMicros;
        }
        
        
        public boolean process( GL gl, int w, int h ) {
            ByteBuffer buf = mReader.readFrame( gl, w, h );
            try {
                return mWriter.offer( buf, w, h );
            } catch( IOException ex ) {
                ex.printStackTrace();
                return false;
            }
        }
        
        
        public void close() {
            try {
                mWriter.close();
            } catch( IOException ex ) {
                ex.printStackTrace();
            }

            List<Runnable> list = null;
            synchronized( this ) {
                if( mCompletionCallbacks.isEmpty() ) {
                    return;
                }
                list = new ArrayList<Runnable>( mCompletionCallbacks );
                mCompletionCallbacks.clear();
            }
            
            for( Runnable r: list ) {
                r.run();
            }
        }
        
        
        @Override
        public int compareTo( Stream s ) {
            return mStartMicros < s.mStartMicros ? -1 : 1;
        }
        
        
        public synchronized void registerCompletionCallback( Runnable r ) {
            mCompletionCallbacks.add( r );
        }
    }
    
    
    private static final class FlushThread extends Thread {

        
        private final List<Joinable> mList = new ArrayList<Joinable>();
        

        public FlushThread() {
            setName( "Video Shutdown Thread" );
            Runtime.getRuntime().addShutdownHook( this );
        }
        
        
        synchronized void add( Joinable item ) {
            mList.add( item );
        }
        
        
        synchronized void remove( Joinable item ) {
            mList.remove( item );
        }
        
        
        public void run() {
            List<Joinable> list = null;
            
            synchronized( this ) {
                if( mList.isEmpty() ) {
                    return;
                }
                list = new ArrayList<Joinable>( mList );
            }
            
            for( Joinable j: list ) {
                try {
                    j.close();
                } catch( IOException ex ) {
                    ex.printStackTrace();
                }
            }
            
            for( Joinable j: list ) {
                try {
                    j.join();
                } catch( InterruptedException ex ) {
                    break;
                }
            }
        }
        
    }
    
}
