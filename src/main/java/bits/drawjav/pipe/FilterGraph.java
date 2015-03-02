package bits.drawjav.pipe;

import bits.collect.RingList;
import bits.drawjav.Packet;
import bits.drawjav.StreamHandle;
import com.google.common.eventbus.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author Philip DeCamp
 */
public class FilterGraph {

    private static final Logger sLog = Logger.getLogger( FilterGraph.class.getName() );

    public static final int OKAY     = 0;
    public static final int WAIT     = 1;
    public static final int FINISHED = 2;

    //private final ExecutionQueue mEventQueue = new ExecutionQueue();
    private final EventBus mBus;
    private final ExecutionQueue mExecutor = new ExecutionQueue();

    private final Map<Filter, FilterNode> mFilterMap = new LinkedHashMap<Filter, FilterNode>();
    private final Map<InPad, InNode>      mInMap     = new HashMap<InPad, InNode>();
    private final Map<OutPad, OutNode>    mOutMap    = new HashMap<OutPad, OutNode>();

    private RingList<Op> mQueue    = new RingList<Op>();
    private RingList<Op> vRequests = new RingList<Op>();

    private boolean mNeedInit = true;


    FilterGraph() {
        mBus = new AsyncEventBus( mExecutor );
        mBus.register( new RequestHandler() );
    }



    public void register( Filter filter ) {
        nodeFor( filter );
    }


    public void connect( Filter src, OutPad srcPad, Filter dst, InPad dstPad, StreamHandle stream ) throws IOException {
        OutNode a = nodeFor( src ).outputFor( srcPad );
        InNode b = nodeFor( dst ).inputFor( dstPad );
        if( b.mLink != null ) {
            throw new IllegalArgumentException( "Input Pad already linked." );
        }
        if( stream != null ) {
            srcPad.config( stream );
            dstPad.config( stream );
        }

        a.mLinks.add( b );
        b.mLink = a;
    }


    public void postEvent( Object event ) {
        mBus.post( event );
    }


    public synchronized boolean waitForWork( long millis ) {
        processEvents();
        if( !mQueue.isEmpty() ) {
            return true;
        }

        try {
            wait( millis );
        } catch( InterruptedException ignore ) {}

        processEvents();
        return !mQueue.isEmpty();
    }


    public int step() {
        if( mNeedInit ) {
            mNeedInit = false;
            for( FilterNode filter: mFilterMap.values() ) {
                filter.mFilter.open( mBus );
            }
            bootstrapOps();
        }

        processEvents();

        Op op = mQueue.peek();
        if( op == null ) {
            return WAIT;
        }

        op.execute();
        return OKAY;
    }


    public void clear() {
        for( FilterNode f: mFilterMap.values() ) {
            f.mFilter.clear();
            for( InNode p: f.mInputs ) {
                p.clear();
            }
            for( OutNode p: f.mOutputs ) {
                p.clear();
            }
        }
        clearOps();
        bootstrapOps();
    }



    private void processEvents() {
        while( true ) {
            Runnable r = mExecutor.poll();
            if( r == null ) {
                return;
            }
            r.run();
        }
    }


    private void offerOp( Op op ) {
        if( !op.mEnqueued ) {
            op.mEnqueued = true;
            mQueue.offer( op );
        }
    }


    private void pushOpToFront( Op op ) {
        if( op.mEnqueued ) {
            removeOp( op );
        }
        op.mEnqueued = true;
        mQueue.add( 0, op );
    }


    private void clearOps() {
        while( !mQueue.isEmpty() ) {
            mQueue.remove().mEnqueued = false;
        }
    }


    private void bootstrapOps() {
        // Add all output pad ops.
        for( OutNode node: mOutMap.values() ) {
            offerOp( node );
        }
    }


    private void removeOp( Op op ) {
        mQueue.remove( op );
        op.mEnqueued = false;
    }


    private FilterNode nodeFor( Filter filter ) {
        FilterNode ret = mFilterMap.get( filter );
        if( ret != null ) {
            return ret;
        }

        ret = new FilterNode( filter );
        mFilterMap.put( filter, ret );
        mNeedInit = true;
        mBus.register( filter );
        return ret;
    }


    private class FilterNode {
        final Filter mFilter;
        final List<InNode>  mInputs  = new ArrayList<InNode>();
        final List<OutNode> mOutputs = new ArrayList<OutNode>();

        FilterNode( Filter filter ) {
            mFilter = filter;
        }

        InNode inputFor( InPad pad ) {
            InNode node = mInMap.get( pad );
            if( node != null ) {
                return node;
            }

            InNode ret = new InNode( this, pad );
            mInMap.put( pad, ret );
            mInputs.add( ret );
            return ret;
        }

        OutNode outputFor( OutPad pad ) {
            OutNode node = mOutMap.get( pad );
            if( node != null ) {
                return node;
            }

            OutNode ret = new OutNode( this, pad );
            mOutMap.put( pad, ret );
            mOutputs.add( ret );
            return ret;
        }


        boolean isSource() {
            return mInputs.isEmpty() && !mOutputs.isEmpty();
        }

        boolean isSink() {
            return !mInputs.isEmpty() && mOutputs.isEmpty();
        }

    }


    private class OutNode extends Op {
        final FilterNode mFilter;
        final OutPad     mPad;

        final Packet[] mPacket = { null };
        final List<InNode> mLinks  = new ArrayList<InNode>();
        int mFullLinkNum = 0;


        OutNode( FilterNode filter, OutPad pad ) {
            mFilter = filter;
            mPad = pad;
        }


        @Override
        public void execute() {
            // Check if place to put packet.
            if( mPacket[0] != null ) {
                // Deactivate until links are drained.
                removeOp( this );
                return;
            }

            int err = 0;
            try {
                err = mPad.poll( mPacket );
            } catch( Exception ex ) {
                mHealthy = false;
                sLog.log( Level.SEVERE, "Filter operation failed.", ex );
                removeOp( this );
            }

            switch( err ) {
            case OutPad.OKAY:
                // Send packet to all links.
                // Deactivate until links are drained.
                removeOp( this );
                mFullLinkNum = 0;
                for( InNode n: mLinks ) {
                    if( n.enqueueInput( mPacket[0] ) ) {
                        mFullLinkNum++;
                    }
                }

                // Stop if no one is receiving output.
                if( mFullLinkNum == 0 ) {
                    mHealthy = false;
                    break;
                }
                break;

            case OutPad.UNFINISHED:
                // Leave Op in place and try again.
                // Do not re-insert the op. We don't want to interfere with any high priority clear events,
                // not that that should be much of a problem with current threading model.
                break;

            case OutPad.FILL_FILTER:
                removeOp( this );
                if( mFilter.isSource() ) {
                    // If this is a source, there's nothing we can do.
                    mHealthy = false;
                }
                return;

            case OutPad.WAIT:
                // Remove op for now. We'll receive a OutPadReadyEvent when more data is available.
                removeOp( this );
                return;

            // All of these count as errors that indicate the filter is no longer operating.
            default:
                sLog.severe( "Unknown response from Filter output: " + err );
            case OutPad.CLOSED:
            case OutPad.EXCEPTION:
                removeOp( this );
                mHealthy = false;
                break;
            }
        }


        public void dequeueLink() {
            if( --mFullLinkNum <= 0 ) {
                if( mPacket[0] != null ) {
                    mPacket[0].deref();
                    mPacket[0] = null;
                }
                checkStatus();
            }
        }


        public void clear() {
            mHealthy = true;
            mFullLinkNum = 0;
            if( mPacket[0] != null ) {
                mPacket[0].deref();
                mPacket[0] = null;
            }
        }


        private void checkStatus() {
            if( !mHealthy || mPacket[0] != null ) {
                return;
            }

            if( mPad.status() == OutPad.OKAY ) {
                // Check if more output.
                offerOp( this );
            } else {
                // Check for more input.
                for( InNode in: mFilter.mInputs ) {
                    if( in.mPacket != null ) {
                        offerOp( in );
                    }
                }
            }
        }

    }


    private class InNode extends Op {
        final FilterNode mFilter;
        final InPad      mPad;

        Packet  mPacket;
        OutNode mLink;


        InNode( FilterNode filter, InPad pad ) {
            mFilter = filter;
            mPad = pad;
        }

        @Override
        public void execute() {
            int err = 0;
            try {
                err = mPad.offer( mPacket );
            } catch( Exception ex ) {
                removeOp( this );
                setHealthy( false );
                sLog.log( Level.SEVERE, "Filter operation failed.", ex );
            }

            switch( err ) {
            case InPad.OKAY:
                removeOp( this );
                clearPacket();
                // Activate outputs.
                for( OutNode out : mFilter.mOutputs ) {
                    offerOp( out );
                }
                break;

            case InPad.UNFINISHED:
                // Leave Op in place and try again.
                break;

            case InPad.DRAIN_FILTER:
                removeOp( this );
                // If this is a sink, there's nothing we can do.
                if( mFilter.isSink() ) {
                    setHealthy( false );
                    break;
                }
                // Activate all outputs on filter.
                for( OutNode out : mFilter.mOutputs ) {
                    if( out.mHealthy ) {
                        offerOp( out );
                    }
                }
                break;

            case InPad.WAIT:
                // Remove op for now. We'll receive a InPadReadyEvent when more data is available.
                removeOp( this );
                break;

            default:
                sLog.severe( "Unknown response from Filter input pad: " + err );
            case InPad.CLOSED:
            case InPad.EXCEPTION:
                removeOp( this );
                mHealthy = false;
                break;
            }
        }


        public void clear() {
            mHealthy = true;
            if( mPacket != null ) {
                mPacket.deref();
                mPacket = null;
            }
        }


        boolean enqueueInput( Packet packet ) {
            if( !mHealthy ) {
                return false;
            }

            if( mPacket != null ) {
                mPacket.deref();;
                mPacket = null;
            }
            if( packet != null ) {
                mPacket = packet;
                mPacket.ref();
            }
            offerOp( this );
            return true;
        }


        private void setHealthy( boolean h ) {
            if( h == mHealthy ) {
                return;
            }
            mHealthy = h;
            if( !mHealthy ) {
                clearPacket();
            }
        }


        private void clearPacket() {
            if( mPacket == null ) {
                return;
            }
            mPacket.deref();
            mPacket = null;
            if( mLink != null ) {
                mLink.dequeueLink();
            }
        }

    }



    private class RequestHandler {
        @Subscribe
        public void process( InPadReadyEvent event ) {
            synchronized( FilterGraph.this ) {
                InNode node = mInMap.get( event.mPad );
                if( node != null ) {
                    offerOp( node );
                }
            }
        }

        @Subscribe
        public void process( OutPadReadyEvent event ) {
            synchronized( FilterGraph.this ) {
                OutNode node = mOutMap.get( event.mPad );
                if( node != null ) {
                    offerOp( node );
                }
            }
        }
    }


    private abstract class Op {
        boolean mHealthy = true;

        boolean mEnqueued  = false;
        Op      mQueueNext = null;

        abstract void execute();
    }


    private class ExecutionQueue implements Executor {

        private Queue<Runnable> mQ = new LinkedList<Runnable>();

        @Override
        public void execute( Runnable runnable ) {
            synchronized( FilterGraph.this ) {
                if( mQ.isEmpty() ) {
                    FilterGraph.this.notify();
                }
                mQ.offer( runnable );
            }
        }

        Runnable poll() {
            synchronized( this ) {
                return mQ.poll();
            }
        }

    }

}
