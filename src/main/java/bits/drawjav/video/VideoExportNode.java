/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;

import static com.jogamp.opengl.GL3.*;

import bits.draw3d.*;
import bits.glui.GPanel;
import bits.jav.util.Rational;
import bits.microtime.*;
import bits.png.NativeZLib;
import bits.png.PngBufferWriter;
import bits.util.Files;
import bits.util.OutputFileNamer;
import bits.util.ref.*;


/**
 * Encodes frame buffer directly to H264/MP4 file.
 * May be used as a DrawNode or a GPanel.
 * Encoding is performed on separate thread for better performance.
 * Multiple video captures may be run in parallel.
 * Video captures may be scheduled at any time.
 * Video captures may be closed at any time using the Closeable handle provided by {@code stopMicros}.
 * Video captures are closed automatically and safely on system exit events.
 * 
 * @author decamp
 */
public class VideoExportNode extends GPanel implements DrawNode, ReshapeListener {
    
    public static final int QUALITY_HIGHEST = 100;
    public static final int QUALITY_LOWEST  = 0;
    public static final int QUALITY_DEFAULT = 30;

    public static final int PNG_COMPRESSION_NONE             = NativeZLib.Z_NO_COMPRESSION;
    public static final int PNG_COMPRESSION_BEST_SPEED       = NativeZLib.Z_BEST_SPEED;
    public static final int PNG_COMPRESSION_BEST_COMPRESSION = NativeZLib.Z_BEST_COMPRESSION;
    public static final int PNG_COMPRESSION_DEFAULT          = NativeZLib.Z_DEFAULT_COMPRESSION;

    private static final int OVERHEAD  = 1024;
    private static final int ROW_ALIGN = 4;

    private static final Double GAMMA = 1.0 / 2.2;
    

    private static final int MAX_QUEUE_SIZE = 2;
    
    private final Clock mClock;
    private final PriorityQueue<Stream> mNewStreams = new PriorityQueue<Stream>();
    private final List<Stream> mStreams = new ArrayList<Stream>();
    private final FlushThread mFlusher  = new FlushThread();
    
    private int     mReadTarget     = GL_COLOR_ATTACHMENT0;
    private boolean mDoubleBuffered = false;

    
    public VideoExportNode( Clock clock ) {
        mClock = clock;
    }
    
    
    /**
     * @param readTarget Specifies buffer to read data from. Must be GL_COLOR_ATTACHMENT* or GL_COLOR_BACK.
     *                   Default is GL_COLOR_ATTACHMENT0.
     */
    public void readTarget( Integer readTarget ) {
        mReadTarget = readTarget;
    }
    
    /**
     * Adds video capture. The video capture will terminate upon one of three events: <br/>
     * 1. The internal clock reaches or exceeds the provided {@code stopMicros} param. <br/>
     * 2. {@code close()} is called on the {@link java.io.Closeable} object returned by this method. <br/>
     * 3. System shutdown, in which case a shutdown hook will attemp to terminate the video capture safely. <br/>
     * <p>
     * Encodings may have constant quality or constant bitrate, specified by the {@code quality} and
     * {@code bitrate} parameters: <br/>
     * {@code quality >= 0 } : Constant quality. <br/>
     * {@code quality <  0, bitrate >= 0 }: Constant bitrate. <br/>
     * {@code quality <  0, bitrate <  0 }: Constant quality of 30.
     *
     * @param outFile     File to write video to. IF outFile.exists(), the existing file will not
     *                    be modified in any way, and a unique number will be added to the 
     *                    filename used.
     * @param quality     Quality of encoding.  0 = highest, 100 = lowest. Negative = use constant bitrate.
     *                    Default is 30 if both {@code quality} and {@code bitrate} parameters are negative.
     * @param bitrate     Bit rate of encoding. Only used if quality is negative.
     * @param startMicros When video catpure should begin. Use Long.MIN_VALUE to begin immediately.
     * @param stopMicros  When video capture should end. Use Long.MAX_VALUE to capture without set duration.
     * @return object that may be closed ({@link java.io.Closeable#close() object.close()}) to end video capture.
     * 
     */
    public Job addVideoWriter( File outFile,
                               int quality,
                               int bitrate,
                               long startMicros,
                               long stopMicros )
                               throws IOException
    {
        outFile = Files.withSuffix( outFile, "mp4" );
        
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
        BgrReader reader   = new BgrReader( pool );
        ColorWriter writer = new ColorWriter( outFile, quality, bitrate, 24, null, pool, mFlusher );
        Stream stream      = new Stream( startMicros, stopMicros, reader, writer );
        mNewStreams.offer( stream );
        
        return stream;
    }


    /**
     * Adds video capture. The video capture will terminate upon one of three events: <br/>
     * 1. The internal clock reaches or exceeds the provided {@code stopMicros} param. <br/>
     * 2. {@code close()} is called on the {@code java.io.Closeable} object returned by this method. <br/>
     * 3. System shutdown, in which case a shutdown hook will attemp to terminate the video capture safely. <br/>
     * <p>
     * Encodings may have constant quality or constant bitrate, specified by the {@code quality} and
     * {@code bitrate} parameters: <br/>
     * {@code quality >= 0 }: Constant quality. <br/>
     * {@code quality < 0, bitrate >= 0 }: Constant bitrate. <br/>
     * {@code quality < 0, bitrate < 0 }: Constant quality of 30.
     *
     * @param outDir      Directory to store images in.
     * @param compLevel   Compression level.
     * @param startMicros When video catpure should begin. Use Long.MIN_VALUE to begin immediately.
     * @param stopMicros  When video capture should end. Use Long.MAX_VALUE to capture without set duration.
     * @return object that may be closed ({@code object.close()}) to end video capture.
     *
     */
    public Job addPngWriter( File outDir,
                             String fileName,
                             int compLevel,
                             long startMicros,
                             long stopMicros )
                             throws IOException
    {
        if( !outDir.exists() && !outDir.mkdirs() ) {
            throw new IOException( "Failed to create dir: " + outDir.getPath() );
        }

        OutputFileNamer namer       = new OutputFileNamer( outDir, fileName, ".png", 5 );
        ObjectPool<ByteBuffer> pool = new HardPool<ByteBuffer>( MAX_QUEUE_SIZE + 1 );
        RgbReader reader            = new RgbReader( pool );
        PngEncoder writer           = new PngEncoder( pool, namer, compLevel, mFlusher );
        Stream stream               = new Stream( startMicros, stopMicros, reader, writer );
        mNewStreams.offer( stream );

        return stream;
    }


    @Override
    public void init( DrawEnv d ) {
        mDoubleBuffered = d.mGld.getChosenGLCapabilities().getDoubleBuffered();
    }

    @Override
    public void dispose( DrawEnv d ) {
        for( Stream s: mStreams ) {
            s.close();
        }
        mStreams.clear();
        for( Stream s: mNewStreams ) {
            s.close();
        }
        mNewStreams.clear();
    }

    @Override
    public void reshape( DrawEnv d ) {
        setSize( d.mViewport.mW, d.mViewport.mH );
    }

    @Override
    public void pushDraw( DrawEnv d ) {}

    @Override
    public void popDraw( DrawEnv d ) {
        processFrame( d );
    }


    @Override
    public void paintComponent( DrawEnv d ) {
        super.paintComponent( d );
        processFrame( d );
    }


    private void processFrame( DrawEnv d ) {
        long t = mClock.micros();

        while( !mNewStreams.isEmpty() && mNewStreams.peek().startMicros() <= t ) {
            Stream s = mNewStreams.remove();
            mStreams.add( s );
        }

        int len = mStreams.size();
        for( int i = 0; i < len; i++ ) {
            Stream s = mStreams.get( i );
            if( s.stopMicros() <= t || !s.process( d, width(), height() ) ) {
                s.close();
                mStreams.remove( i-- );
                len--;
            }
        }
    }

    


    public interface Job extends Closeable, TimeRanged {
        void addCompletionCallback( Runnable r );
    }
    
    
    private interface Joinable extends Closeable {
        void join() throws InterruptedException;
    }
    
    
    private interface FrameReader {
        ByteBuffer readFrame( DrawEnv d, int w, int h );
    }
    
    
    private interface FrameWriter extends Joinable {
        boolean offer( ByteBuffer src, int w, int h ) throws IOException;

        void close() throws IOException;
    }

    
    private final class BgrReader implements FrameReader {
        
        private final ObjectPool<ByteBuffer> mPool;
        
        public BgrReader( ObjectPool<ByteBuffer> pool ) {
            mPool = pool;
        }
        
        public ByteBuffer readFrame( DrawEnv d, int w, int h ) {
            int rowBytes = ( w * 3 + ROW_ALIGN - 1 ) / ROW_ALIGN * ROW_ALIGN;
            int cap = rowBytes * h + OVERHEAD;
            
            ByteBuffer buf = mPool.poll();
            if( buf == null || buf.capacity() < cap ) {
                buf = ByteBuffer.allocateDirect( cap );
            } else {
                buf.clear();
            }
            
            buf.order( ByteOrder.nativeOrder() );
            
            d.mGl.glReadBuffer( mReadTarget );
            d.mGl.glPixelStorei( GL_PACK_ALIGNMENT, ROW_ALIGN );
            d.mGl.glReadPixels( 0, 0, w, h, GL_BGR, GL_UNSIGNED_BYTE, buf );
            buf.position( 0 ).limit( rowBytes * h );
            return buf;
        }
    }


    private final class RgbReader implements FrameReader {

        private final ObjectPool<ByteBuffer> mPool;

        public RgbReader( ObjectPool<ByteBuffer> pool ) {
            mPool = pool;
        }

        public ByteBuffer readFrame( DrawEnv d, int w, int h ) {
            int rowBytes = ( w * 3 + ROW_ALIGN - 1 ) / ROW_ALIGN * ROW_ALIGN;
            int cap = rowBytes * h + OVERHEAD;

            ByteBuffer buf = mPool.poll();
            if( buf == null || buf.capacity() < cap ) {
                buf = ByteBuffer.allocateDirect( cap );
            } else {
                buf.clear();
            }

            buf.order( ByteOrder.nativeOrder() );

            d.mGl.glReadBuffer( mReadTarget );
            d.mGl.glPixelStorei( GL_PACK_ALIGNMENT, ROW_ALIGN );
            d.mGl.glReadPixels( 0, 0, w, h, GL_RGB, GL_UNSIGNED_BYTE, buf );
            buf.position( 0 ).limit( rowBytes * h );
            return buf;
        }

    }
    
    
    private static final class ColorWriter implements FrameWriter, Runnable {
        
        private final File mOutFile;
        private final Mp4Writer mOut = new Mp4Writer();
        private final Queue<ByteBuffer> mQueue = new ArrayDeque<ByteBuffer>( 4 );
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
            mOutFile = outFile;
            if( quality >= 0 ) {
                mOut.quality( quality );
            } else if( bitrate >= 0 ) {
                mOut.bitrate( bitrate );
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

                flip.position( 0 );
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


    private static final class PngEncoder implements FrameWriter, Runnable {

        private final PngBufferWriter mComp = new PngBufferWriter();
        private final ObjectPool<ByteBuffer> mPool;
        private final OutputFileNamer mNamer;
        private final int mCompLevel;
        private final FlushThread mFlusher;

        private Thread mThread = null;
        private boolean mClosed = false;

        private final Queue<ByteBuffer> mQueue = new ArrayDeque<ByteBuffer>( 4 );
        private int mWidth;
        private int mRowSize;
        private int mHeight;
        private ByteBuffer mDstBuf;


        PngEncoder( ObjectPool<ByteBuffer> pool,
                    OutputFileNamer namer,
                    int compLevel,
                    FlushThread flusher )

        {
            mNamer     = namer;
            mPool      = pool;
            mCompLevel = compLevel;
            mFlusher   = flusher;
        }


        public synchronized boolean offer( ByteBuffer buf, int w, int h ) throws IOException {
            if( mThread == null ) {
                if( mClosed ) {
                    return false;
                }

                mWidth  = w;
                mHeight = h;
                mRowSize = ( w * 3 + ROW_ALIGN - 1 ) / w * w;
                mThread = new Thread( this );
                mThread.setName( "Png Encoder" );
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
                while( process() );
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
            int w;
            int h;
            int rowSize;

            synchronized( this ) {
                if( !mQueue.isEmpty() ) {
                    buf = mQueue.remove();
                    notifyAll();
                } else if( !mClosed ) {
                    try {
                        wait();
                    } catch( InterruptedException ignored ){}
                    return true;
                }

                w = mWidth;
                h = mHeight;
                rowSize = mRowSize;
            }

            if( buf == null ) {
                return false;
            }

            int p0  = buf.position();
            //int p1  = p0 + w * h * 3;
            int cap = rowSize * h * 3;
            ByteBuffer dst = mDstBuf;

            if( dst == null || dst.capacity() < cap ) {
                dst = ByteBuffer.allocateDirect( cap + OVERHEAD );
                dst.order( ByteOrder.BIG_ENDIAN );
                mDstBuf = dst;
            }else{
                dst.clear();
            }

            mComp.open( dst, w, h, PngBufferWriter.COLOR_TYPE_RGB, 8, mCompLevel, GAMMA );
            for( int y = 0; y < h; y++ ) {
                int m0 = p0 + (h - y - 1) * rowSize;
                int m1 = m0 + w * 3;
                buf.position( 0 ).limit( m1 ).position( m0 );
                mComp.writeData( buf );
            }
            mComp.close();
            dst.flip();

            if( mPool != null ) {
                mPool.offer( buf );
            }

            File outFile = mNamer.next();
            if( !outFile.getParentFile().exists() ) {
                outFile.getParentFile().mkdirs();
            }

            FileChannel out = new FileOutputStream( outFile ).getChannel();
            try {
                while( dst.remaining() > 0 ) {
                    int n = out.write( dst );
                    if( n <= 0 ) {
                        throw new IOException( "Write failed." );
                    }
                }
            } finally {
                out.close();
            }

            return true;
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
        
        
        public boolean process( DrawEnv d, int w, int h ) {
            ByteBuffer buf = mReader.readFrame( d, w, h );
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
        
        
        public synchronized void addCompletionCallback( Runnable r ) {
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



