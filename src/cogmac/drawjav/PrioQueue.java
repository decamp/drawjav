package cogmac.drawjav;

import java.util.Comparator;

@SuppressWarnings( "unchecked" )
class PrioQueue<T extends DoubleLinkedNode> extends DoubleLinkedNode {
    
    
    public final Comparator<? super T> mComp;
    public T mHead   = null;
    public T mTail   = null;
    public int mSize = 0;
    
    
    public PrioQueue() {
        this( null );
    }
    
    
    public PrioQueue( Comparator<? super T> comp ) {
        mComp = comp;
    }
     
    
    
    public T head() {
        return mHead;
    }
    
    
    public T removeHead() {
        if( mHead == null ) {
            return null;
        }
        T ret = mHead;
        if( mHead.mNext == null ) {
            mHead = null;
            mTail = null;
            mSize = 0;
        } else {
            mHead       = (T)mHead.mNext;
            mHead.mPrev = null;
            ret.mNext   = null;
            mSize--;
        }
        return ret;
    }
    
    
    public T tail() {
        return mTail;
    }
    
    
    public T removeTail() {
        if( mTail == null ) {
            return null;
        }
        T ret = mTail;
        if( mTail.mPrev == null ) {
            mHead = null;
            mTail = null;
            mSize = 0;
        } else {
            mTail       = (T)mTail.mPrev;
            mTail.mNext = null;
            ret.mPrev   = null;
            mSize--;
        }
        return ret;
    }
    
    
    public void offer( T node ) {
        if( mHead == null ) {
            // Only item
            node.mNext = null;
            node.mPrev = null;
            mHead = node;
            mTail = node;
            mSize = 1;
            return;
        }
        
        if( comp( node, mTail ) >= 0 ) {
            // Tail insert
            node.mNext  = null;
            node.mPrev  = mTail;
            mTail.mNext = node;
            mTail = node;
            mSize++;
            return;
        }
        
        if( mTail == mHead || comp( node, mHead ) < 0 ) {
            // Head insert
            node.mNext  = mHead;
            node.mPrev  = null;
            mHead.mPrev = node;
            mHead = node;
            mSize++;
            return;
        }
        
        // Work way up tail.
        T pos = (T)mTail.mPrev;
        while( pos != null ) {
            if( comp( node, pos ) >= 0 ) {
                node.mPrev = pos;
                node.mNext = pos.mNext;
                node.mPrev.mNext = node;
                node.mNext.mPrev = node;
                mSize++;
                return;
            }
            
            pos = (T)pos.mPrev;
        }
        
        // This should not happen. Head insert.
        node.mNext  = mHead;
        node.mPrev  = null;
        mHead.mPrev = node;
        mHead = node;
        mSize++;
    }
    
    
    public void remove( T node ) {
        if( node.mPrev == null ) {
            if( node == mHead ) {
                if( node.mNext == null ) {
                    mHead = null;
                    mTail = null;
                    mSize = 0;
                } else {
                    mHead = (T)node.mNext;
                    mHead.mPrev = null;
                    mSize--;
                }
            }
        } else if( node.mNext != null ) {
            node.mPrev.mNext = node.mNext;
            node.mNext.mPrev = node.mPrev;
            mSize--;
        } else if( node == mTail ) {
            mTail = (T)node.mPrev;
            mTail.mNext = null;
            mSize--;
        }
        
        node.mNext = null;
        node.mPrev = null;
    }
    
    
    public void reschedule( T node ) {
        if( node.mNext != null && 
            comp( node, (T)node.mNext ) > 0 ) 
        {
            remove( node );
            offer( node );
            return;
        }
        
        if( node.mPrev != null &&
            comp( node, (T)node.mPrev ) < 0 )
        {
            remove( node );
            offer( node );
            return;
        }
    }
    
    
    public void clear() {
        DoubleLinkedNode pos = mHead;
        mSize = 0;
        mHead = null;
        mTail = null;
        
        while( pos != null ) {
            DoubleLinkedNode next = pos.mNext;
            pos.mPrev = null;
            pos.mNext = null;
            pos = next;
        }
    }
    
    
    public int size() {
        return mSize;
    }
    
    
    private int comp( T a, T b ) {
        return mComp != null ? mComp.compare( a, b ) : ((Comparable<? super T>)a).compareTo( b );
    }

}
