package digitSequence;

/**
 * 
 * A map for which the keys are arrays of long integers, and the values are long integers.
 * This map cannot be expanded beyond its initial size.
 */
public class FixedSizeMultiLongLongMap {

	private final int hashLength;
	private final int[] hashTable;
	private final long[] array;
	private int size = 0;
	private final int storage;
	private final int keySize;
	private final long BITMASK = 0x00000000FFFFFFFFL;
	private boolean full;
	
	public FixedSizeMultiLongLongMap(int hashLength, int storage, int keySize) {
		this.storage = storage;
		this.hashLength = hashLength;
		this.hashTable = new int[(int) Math.pow(2, hashLength)];
		this.array = new long[(keySize + 2) * storage + 1];
		this.keySize = keySize;
	}
	
	public synchronized long get(long[] key){
		
		int hash = 0;
		
		for (int i = 0; i < keySize; i++)
			hash ^= Long.hashCode(key[i]) >>> (32 - hashLength);
		
		int pointer = hashTable[hash];
		while (pointer != 0){
		
			boolean found = true;
			for (int i = 0; i < keySize; i++) {
				if (key[i] != array[pointer + i]) {
					found = false;
					break;
				}
			}
			if (found)
				return array[pointer + keySize];
			
			boolean trialKeyGreater = false;
			
			for (int i = 0; i < keySize; i++) {
				if (array[pointer + i] > key[i]) {
					trialKeyGreater = true;
					break;
				}
				else if (array[pointer + i] < key[i])
					break;
			}
			
			if (trialKeyGreater)
				pointer = (int) (array[pointer + keySize + 1] >>> 32);
			else
				pointer = (int) (array[pointer + keySize + 1] & BITMASK);
		
		}
		
		return -1;
	}
	
	public synchronized void put(long[] key, long value){
		
		if (full)
				return;
		
		int hash = 0;
		
		for (int i = 0; i < keySize; i++)
			hash ^= Long.hashCode(key[i]) >>> (32 - hashLength);
		
		int pointer = hashTable[hash];
		
		if (pointer == 0){
			pointer = (keySize + 2) * size + 1;
			size++;
			if (size == storage)
				full = true;
			hashTable[hash] = pointer;
			for (int i = 0; i < keySize; i++)
				array[pointer + i] = key[i];
			array[pointer + keySize] = value;
			return;
		}
		
		int oldPointer = 0;
		while (pointer != 0){
			
			boolean found = true;
			for (int i = 0; i < keySize; i++) {
				if (key[i] != array[pointer + i]) {
					found = false;
					break;
				}
			}
			if (found)
				return;
			
			oldPointer = pointer;
			
			boolean trialKeyGreater = false;
			
			for (int i = 0; i < keySize; i++) {
				if (array[pointer + i] > key[i]) {
					trialKeyGreater = true;
					break;
				}
				else if (array[pointer + i] < key[i])
					break;
			}
			
			if (trialKeyGreater)
				pointer = (int) (array[pointer + keySize + 1] >>> 32);
			else
				pointer = (int) (array[pointer + keySize + 1] & BITMASK);
			
		}
		
		pointer = (keySize + 2) * size + 1;
		size++;
		if (size == storage)
			full = true;
		
		for (int i = 0; i < keySize; i++)
			array[pointer + i] = key[i];
		array[pointer + keySize] = value;
		
		boolean keyGreater = false;
		
		for (int i = 0; i < keySize; i++) {
			if (key[i] > array[oldPointer + i]) {
				keyGreater = true;
				break;
			}
			else if (key[i] < array[oldPointer + i] )
				break;
		}
		
		if (keyGreater)
			array[oldPointer + keySize + 1] |= pointer;
		else
			array[oldPointer + keySize + 1] |= ((long) pointer << 32);
	}
	
	public int size(){
		return size;
	}
	
}
