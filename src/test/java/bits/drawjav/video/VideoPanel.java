package bits.drawjav.video;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.util.*;

import javax.swing.JPanel;

import bits.drawjav.*;
import bits.jav.Jav;
import bits.jav.util.Rational;


/**
 * @author decamp
 */
public class VideoPanel extends JPanel implements Sink<VideoPacket> {

    private final VideoPacketResampler mResampler = new VideoPacketResampler( null );
    private VideoPacket mSrc         = null;
    private BufferedImage mImage     = null;
    private int[] mRow               = null;
    
    private int mFrameWidth  = -1;
    private int mFrameHeight = -1;
    private int mPanelWidth  = -1;
    private int mPanelHeight = -1;
    private Box mDstBox      = null;
    
    Set<Object> testSet = new HashSet<Object>();
    
    
    public VideoPanel() {
        setDoubleBuffered( false );
    }
    
    
    @Override
    public void consume( VideoPacket packet ) throws IOException {
        if( mSrc != null ) {
            mSrc.deref();
            mSrc = null;
        }
        
        mSrc = packet;
        
        if( mSrc != null ) {
            mSrc.ref();
        } 
        
        repaint();
    }
    
    
    @Override
    public synchronized void clear() {
        if( mSrc == null ) {
            return;
        }
        mSrc.deref();
        mSrc = null;
        repaint();
    }

    
    @Override
    public void close() throws IOException {}
    
    
    public boolean isOpen() {
        return true;
    }

    
    @Override
    public void paintComponent( Graphics gg ) {
        final Graphics2D g = (Graphics2D)gg;
        final int w = getWidth();
        final int h = getHeight();
        
        VideoPacket frame = null;
        synchronized( this ) {
            frame = mSrc;
            if( frame != null ) {
                frame.ref();
            }
        }
        
        if( frame == null ) {
            g.setBackground( Color.GRAY );
            g.clearRect( 0, 0, w, h );
            return;
        }
        
        g.setBackground( Color.BLACK );
        g.clearRect( 0, 0, w, h );
        if( !initSize( frame.width(), frame.height(), w, h ) ) {
            frame.deref();
            return;
        }
        
        try {
            VideoPacket p = mResampler.process( frame );
            bufferImage( p );
            g.drawImage( mImage, mDstBox.x(), mDstBox.y(), mDstBox.width(), mDstBox.height(), null );
            p.deref();
        } catch( IOException ex ) {
            ex.printStackTrace();
        }
        
        frame.deref();
    }
    
    
    private boolean initSize( int frameWidth, int frameHeight, int panelWidth, int panelHeight ) {
        if( frameWidth == 0 || frameHeight == 0 ) {
            return false;
        }
        
        if( frameWidth  == mFrameWidth  && 
            frameHeight == mFrameHeight &&
            panelWidth  == mPanelWidth  &&
            panelHeight == mPanelHeight ) 
        {
            return true;
        }
        
        mFrameWidth  = frameWidth;
        mFrameHeight = frameHeight;
        mPanelWidth  = panelWidth;
        mPanelHeight = panelHeight;
        Box box = Box.fromBounds( 0, 0, frameWidth, frameHeight );
        mDstBox = box.fit( Box.fromBounds( 0, 0, panelWidth, panelHeight ) );
        
        int dw = mDstBox.width();
        int dh = mDstBox.height();
        
        PictureFormat fmt = new PictureFormat( dw, dh, Jav.AV_PIX_FMT_BGRA, new Rational( 1, 1 ) );
        mResampler.setDestFormat( fmt );
        return true;
    }
    

    private void bufferImage( VideoPacket packet ) {
        int w = packet.width();
        int h = packet.height();
        int lineSize  = packet.lineSize( 0 );
        ByteBuffer bb = packet.directBuffer();
        bb.order( ByteOrder.LITTLE_ENDIAN );
        
        BufferedImage im = mImage;
        if( im == null || im.getWidth() != w || im.getHeight() != h ) {
            mImage = im = new BufferedImage( w, h, BufferedImage.TYPE_INT_ARGB );
        }
        
        int[] row = mRow;
        if( row == null || row.length < w ) {
            mRow = row = new int[w];
        }
        
        for( int y = 0; y < h; y++ ) {
            bb.position( y * lineSize );
            for( int x = 0; x < w; x++ ) {
                row[x] = bb.getInt();
            }
            mImage.setRGB( 0, y, w, 1, row, 0, w );
        }
    }
   
    
}
