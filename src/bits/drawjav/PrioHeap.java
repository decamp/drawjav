package bits.drawjav;

import java.util.*;


/**
 * Crappy Priority Queue.
 * 
 * @author decamp
 */
@SuppressWarnings( {"unchecked", "rawtypes"} )
class PrioHeap<T extends HeapNode> extends HeapNode {
    
    private final Comparator mComp;
    private HeapNode[] mArr;
    private int mSize = 0;
    
    
    public PrioHeap() {
        this( null );
    }
    
    
    public PrioHeap( Comparator<? super T> comp ) {
        mComp = comp;
        mArr  = new HeapNode[10];
    }
     
    
    
    public void offer( T node ) {
        ensureCapacity( mSize + 1 );
        insertNode( node );
    }
    
    
    
    public T peek() {
        return mSize > 0 ? (T)mArr[0] : null;
    }
    
    
    public T poll() {
        return mSize == 0 ? null : (T)deleteNode( 0 );
    }
    
    
    public T remove() {
        if( mSize == 0 ) {
            throw new NoSuchElementException();
        }
        return (T)deleteNode( 0 );
    }
    
    
    public T remove( int idx ) {
        if( idx < 0 || idx >= mSize ) {
            throw new NoSuchElementException();
        }
        return (T)deleteNode( idx );
    }
    
    
    public boolean remove( T node ) {
        int idx = node.mHeapIndex;
        if( idx < 0 || idx >= mSize || mArr[idx] != node ) {
            return false;
        }
        
        deleteNode( idx );
        return true;
    }
    
    
    public void reschedule( T node ) {
        int idx = node.mHeapIndex;
        if( idx < 0 || idx >= mSize ) {
            throw new NoSuchElementException();
        }
        
        HeapNode prev = deleteNode( idx );
        if( prev != node ) {
            insertNode( prev );
            throw new NoSuchElementException( "Invalid node index." );
        }
        
        insertNode( node );
    }

    
    public T get( int idx ) {
        return (T)mArr[idx];
    }
    
    
    public void clear() {
        for( int i = 0; i < mSize; i++ ) {
            mArr[i].mHeapIndex = -1;
            mArr[i] = null;
        }
        mSize = 0;
    }
    
    
    public int size() {
        return mSize;
    }

    
    public boolean isEmpty() {
        return mSize == 0;
    }
    
    
    public void ensureCapacity( int minCap ) {
        final int oldCap = mArr.length;
        if( minCap <= oldCap ) {
            return;
        }
        
        int newCap = ( oldCap * 3 ) / 2 + 1;
        if( newCap < minCap ) {
            newCap = minCap;
        }
        
        mArr = Arrays.copyOf( mArr, newCap );
    }
    
    
    @Override
    public String toString() {
        StringBuilder s = new StringBuilder( "Heap: " );
        int start = 0;
        int stop  = Math.min( 1, mSize );
        
        while( start < mSize ) {
            s.append( "\n  " );
            
            for( int i = start; i < stop; i++ ) {
                if( i > start ) {
                    s.append( ", " );
                }
                s.append( mArr[i] );
            }
            
            int len = ( stop - start ) * 2;
            start = stop;
            stop  = Math.min( mSize, start + len );
        }
        
        return s.toString();
    }

    
    
    private int comp( Object a, Object b ) {
        return mComp != null ? mComp.compare( a, b ) : ((Comparable<Object>)a).compareTo( b );
    }

    
    private HeapNode deleteNode( int idx ) {
        int size = --mSize;
        HeapNode ret  = mArr[idx];
        HeapNode item = mArr[size];
        mArr[size] = null;
        ret.mHeapIndex = -1;
        
        if( idx == size ) {
            return ret;
        }
        
        // Trickle down.
        int j = (idx<<1)+2;
        while( j < size ) {
            // Determine the smaller of the two children.
            if( comp( mArr[j-1], mArr[j] ) <= 0 ) {
                j--;
            }
            
            // If item is smaller than smallest child, insert.
            if( comp( item, mArr[j] ) <= 0 ) {
                mArr[idx] = item;
                item.mHeapIndex = idx;
                return ret;
            }
            
            HeapNode tmp = mArr[j];
            mArr[idx] = tmp;
            tmp.mHeapIndex = idx;
            
            idx = j;
            j = (j<<1)+2;
        }

        // Special case in which node only has one child.
        
        if( j < size+1 && comp( item, mArr[j-1] ) > 0 ) {
            HeapNode tmp = mArr[--j];
            mArr[idx] = tmp;
            tmp.mHeapIndex = idx;
            idx = j;
        }
        
        mArr[idx] = item;
        item.mHeapIndex = idx;
        
        return ret;
    }

    
    private void insertNode( HeapNode node ) {
        int idx = mSize++;
        while( idx > 0 ) {
            HeapNode parent = mArr[(idx-1)>>1];
            
            if( comp( parent, node ) <= 0 ) {
                break;
            }
            
            mArr[idx] = parent;
            parent.mHeapIndex = idx;
            idx = (idx-1)>>1;
        }
        
        mArr[idx] = node;
        node.mHeapIndex = idx;
    }
    
}
