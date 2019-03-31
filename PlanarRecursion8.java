package digitSequence;

import java.util.Arrays;
import java.util.Date;

public class PlanarRecursion8 implements Runnable{

	private final static long[] work = new long[41]; //Not reliable when using multiple threads
	private final FixedSizeMultiLongLongMap memo; //A cache used to store the number of solutions matching particular patterns of filled and empty spaces 
	private final int maxMemoLevel = 40;
	private final int minMemoLevel;
	private final long[] powers;
	private final int maxN;
	private final int[] digits; //Contains the Langford sequence under construction
	private final int[][] caps; // int[2][length] - For position k, [0][k] contains the pair linked by its "cap" above the line
								// and [1][k] contains the pair linked by its cap, below the line. The initial 0 indicates no cap.
	private final int[][] capSizes;
	private final int[][] capStarts;
	private final int[] digitsOnTop; //Contains the pairs in the Langford sequence connected "above the line"  
	private final int[] required = new int[2048]; //See run method
	private MultiLongLongMap sequences;
	private final int length; // 2 * maxN
	//The next seven arrays are used when constructing the cache key
	private final int[] segmentPositions = new int[30];
	private final int[] segmentStart = new int[30];
	private final int[] segmentEnd = new int[30];
	private final int[] segmentGaps = new int[30];
	private final int[] segmentSort = new int[30];
	private final boolean[] segmentEmpty = new boolean[30];
	private final boolean[] segmentReverse = new boolean[30];
	private final int[] segmentInfo = new int[400]; //Each block of five consecutive ints stores the following information about a segment
													//firstGap (position of the first empty space)
													//lastGap (position of the last empty space)
													//required (digits that must exist in a planar solution to this segment as defined in the run method)
													//previous (a pointer to the index of the previous segment, in this array)
													//next (a pointer to the index of the next segment, in this array)
	private final static long cacheHitValues[] = new long[16];
	private static boolean[] processed;
	private static long results = 0;
	static int cacheHits;
	private long totalWork;
	static long globalWork;
	
	private final int ONE_K = 1024;
	private final int TWO_K = 2048;
	private final int FOUR_K = 4096;
	private final int EIGHT_K = 8192;
	private final int FIFTEEN_K = ONE_K + TWO_K + FOUR_K + EIGHT_K;
	
	
	private static class Memo {
	
		private final FixedSizeMultiLongLongMap memo;
		private final long[] powers;
		
		private Memo(int cacheSize) {
			powers = new long[64];
			powers[0] = 1;
			for (int i = 1; i < 64; i++) 
				powers[i] = 2 * powers[i-1];
			
			int hashSize = 1;
			
			while(powers[hashSize] < cacheSize)
				hashSize++;
			
			memo = new FixedSizeMultiLongLongMap(hashSize, cacheSize, 4);
		}

		private FixedSizeMultiLongLongMap getMemo() {
			return memo;
		}
		
	}
	
	/**
	 * 
	 * @param args
	 * The first argument should be the number of pairs in the Langford sequence (maxN).
	 * The second argument is the smallest pair for which we read from and write to the cache, maxN - 14
	 * appears to be a suitable value.
	 * The third argument is the size of the cache, 20000000 is reasonable.
	 * The fourth argument is the number of threads to use, which depends on your computer.
	 * 
	 * I would recommend running with the -XX:MaxInlineSize=1000 VM argument to ensure that the processSegment
	 * method is in-lined.
	 */
	public static void main(String[] args) {

		System.out.println(Runtime.getRuntime().maxMemory());

		Memo memo = new Memo(Integer.parseInt(args[2]));
		
		System.out.println(new Date());
		
		processed = new boolean[80];
		
		int threadCount = Integer.parseInt(args[3]);

		Thread[] threads = new Thread[threadCount];
		
		int maxN = Integer.parseInt(args[0]);
		
		for (int i = 0; i < threadCount; i++) {
			PlanarRecursion8 recursion = new PlanarRecursion8(maxN,
																Integer.parseInt(args[1]),
																memo.getMemo());
			Thread thread = new Thread(recursion);
			thread.start();
			threads[i] = thread;
		}
		
		for (int i = 0; i < threadCount; i++) {
			if (threads[i].isAlive())
				try {
					threads[i].join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			
		}
		System.out.println("Results: " + results);
		long globalWork = 0;
		for (int i=0; i < maxN +1; i++ ){
			System.out.println(i + " " + work[i]);
			globalWork += work[i];
		}
		
		System.out.println("Total Work: " + globalWork);
		System.out.println("Cache hits: " + cacheHits);
		System.out.println(memo.getMemo().size() + " entries in cache");
		for (int i=0 ; i < 16; i++ ){
			System.out.println((i + 1) + " segments: cacheHitCount= " + cacheHitValues[i]);
		}
		
		System.out.println(new Date());
	}
	
	private static synchronized int getNextPosition(int length, int maxN) {
		
		int position = -1;
		for (int i = 0; i < maxN - 1; i++) {
			if (!processed[i]) {
				processed[i] = true;
				position = i;
				break;
			}
		}
		return position;
	}
	
	private PlanarRecursion8(final int maxN, final int minMemoLevel, final FixedSizeMultiLongLongMap memo) {
		this.maxN = maxN;
		digits = new int[2 * maxN];
		caps = new int[2][2 * maxN];
		capSizes = new int[2][2 * maxN];
		capStarts = new int[2][2 * maxN];
		digitsOnTop = new int[2 * maxN];
		this.minMemoLevel = minMemoLevel;
		powers = new long[64];
		
		length = maxN * 2;
		powers[0] = 1;
		for (int i = 1; i < 64; i++) 
			powers[i] = 2 * powers[i-1];
		
		this.memo = memo;
	}
	
	public void run() {
		
		/*
		 * The required array contains assertions about the solubility of segments of the sequence. The
		 * leading 1 defines the size of the pattern, and the other digits represent filled or empty spaces
		 * in the segment.
		 * 
		 * For example, required[0b100] = -1 means that a segment consisting of just two empty spaces is insoluble.
		 * required[0b1010100] = 9 means that a planar solution for a segment of the form: Empty-Filled-Empty-Filled-Empty-Empty
		 * must include the digits 4 (represented by 8 = 2^(4 - 1)), and 1 (represented by 1 = 2 ^ (1 - 1)).
		 * required[0b10110010] = ONE_K means that a segment of this form must include at least two of the digits 1, 2, and 3.
		 * TWO_K means at least two out of the digits 1, 2, and 4 are required.
		 * FOUR_K means at least two out of the digits 1, 3, and 4 are required.
		 * EIGHT_K means at least two out of the digits 2, 3, and 4 are required.
		 */
		
		required[0b100] = -1;
		required[0b1010] = 1;
		required[0b10000] = -1;
		required[0b10110] = 2;
		required[0b101000] = 3 + ONE_K;
		required[0b100010] = 3 + ONE_K;
		required[0b100100] = 5 + ONE_K;
		required[0b101110] = 4;
		required[0b1000000] = 7 + FIFTEEN_K;
		required[0b1011000] = 5 + ONE_K;
		required[0b1000110] = 5 + ONE_K;
		required[0b1001100] = 10 + EIGHT_K;
		required[0b1010100] = 9;
		required[0b1001010] = 9;
		required[0b1010010] = -1;
		required[0b1011110] = 8;
		required[0b10100000] = 11 + FIFTEEN_K;
		required[0b10000010] = 11 + FIFTEEN_K;
		required[0b10010000] = 19 + ONE_K;
		required[0b10000100] = 19 + ONE_K;
		required[0b10001000] = 21 + ONE_K;
		required[0b10110010] = ONE_K;
		required[0b10100110] = ONE_K;
		required[0b10111000] = 9;
		required[0b10001110] = 9;
		required[0b10110100] = 17;
		required[0b10010110] = 17;
		required[0b10110010] = 2;
		required[0b10100110] = 2;
		required[0b10011100] = 20;
		required[0b10111110] = 16;
		
		required[0b100000000] = 3 + ONE_K;
		required[0b101100000] = 1 + ONE_K;
		required[0b100000110] = 1 + ONE_K;
		required[0b101010000] = 35 + ONE_K;
		required[0b100001010] = 35 + ONE_K;
		required[0b101001000] = 5 + ONE_K;	
		required[0b100010010] = 5 + ONE_K; 
		required[0b101000100] = 2 + ONE_K;
		required[0b100100010] = 2 + ONE_K;
		required[0b101000010] = 13 + FIFTEEN_K;
		required[0b100110000] = ONE_K;
		required[0b100001100] = ONE_K;
		required[0b100101000] = 41;
		required[0b100010100] = 41;
		required[0b100100100] = -1;
		required[0b100011000] = 42 + EIGHT_K;
		
		required[0b101111000] = 17;
		required[0b100011110] = 17;
		required[0b101110100] = 33;
		required[0b100101110] = 33;
		required[0b101101100] = 34;
		required[0b100110110] = 34;
		required[0b101101010] = 1;
		required[0b101010110] = 1;
		required[0b101100110] = -1;
		required[0b101011100] = 36;
		required[0b100111010] = 36;
		required[0b101011010] = 34;
		required[0b100111100] = 40;
		
		required[0b101111110] = 32;
		
		required[0b1010000000] = 1 + FIFTEEN_K;
		required[0b1000000010] = 1 + FIFTEEN_K;
		required[0b1001000000] = 8 + FIFTEEN_K;
		required[0b1000000100] = 8 + FIFTEEN_K;
		required[0b1000100000] = 2 + ONE_K;
		required[0b1000001000] = 2 + ONE_K;
		required[0b1000010000] = 5 + ONE_K;
		
		required[0b1011100000] = 1 + ONE_K;
		required[0b1000001110] = 1 + ONE_K;
		required[0b1011010000] = 67 + ONE_K;
		required[0b1000010110] = 67 + ONE_K;
		required[0b1010110000] = ONE_K;
		required[0b1000011010] = ONE_K;
		required[0b1011001000] = 69 + ONE_K;
		required[0b1000100110] = 69 + ONE_K;
		required[0b1010101000] = 73;
		required[0b1000101010] = 73;
		required[0b1001101000] = 1;
		required[0b1000101100] = 1;
		required[0b1010011000] = 10 + EIGHT_K;
		required[0b1000110010] = 10 + EIGHT_K;
		required[0b1000110100] = 82;
		required[0b1001011000] = 82;
		required[0b1000111000] = 84;
		required[0b1011000100] = 1 + ONE_K;
		required[0b1001000110] = 1 + ONE_K;
		required[0b1010100100] = -1;
		required[0b1001001010] = -1;
		required[0b1001100100] = 67 + ONE_K;
		required[0b1001001100] = 67 + ONE_K;
		required[0b1010010100] = 1;
		required[0b1001010010] = 1;
		required[0b1001010100] = 81;
		required[0b1010001100] = 1 + ONE_K;
		required[0b1001100010] = 1 + ONE_K;
		required[0b1011000010] = 21 + ONE_K;
		required[0b1010000110] = 21 + ONE_K;
		required[0b1010100010] = 2 + ONE_K;
		required[0b1010001010] = 2 + ONE_K;
		required[0b1010010010] = 69 + ONE_K;
		
		required[0b1011111000] = 33;
		required[0b1000111110] = 33;
		required[0b1011110100] = 65;
		required[0b1001011110] = 65;
		required[0b1011101100] = 66;
		required[0b1001101110] = 66;
		required[0b1011101010] = 1;
		required[0b1010101110] = 1;
		required[0b1011100110] = 4 + EIGHT_K;
		required[0b1011001110] = 4 + EIGHT_K;
		required[0b1011011100] = 68;
		required[0b1001110110] = 68;
		required[0b1011011010] = 2;
		required[0b1010110110] = 2;
		required[0b1011010110] = 65;
		required[0b1011010110] = 65;
		required[0b1010111100] = 72;
		required[0b1001111010] = 72;
		required[0b1010111010] = 68;
		required[0b1001111100] = 80;
		
		
		
		sequences = new MultiLongLongMap(12, 5000, 8, true);
		
		//Find the next unprocessed position
		
		while (true) {
			
			int position = getNextPosition(length, maxN);
				
			if (position == -1) {
				return;
			}

			for (int i = 0; i < length; i++) {
				capSizes[0][i] = length;
				capSizes[1][i] = length;
				capStarts[0][i] = 0;
				capStarts[1][i] = 0;
				caps[0][i] = 0;
				caps[1][i] = 0;
			}
			digits[position] = maxN;
			digits[position + maxN + 1] = maxN;
			digitsOnTop[position] = maxN;
			digitsOnTop[position + maxN + 1] = maxN;
			for (int k = 0; k < position; k++) 
				capSizes[0][k] = position;
			for (int k = position + 1; k < position + maxN + 1; k++) {
				caps[0][k] = maxN;
				capStarts[0][k] = position + 1;
				capSizes[0][k] = maxN;
			}
			for (int k = position + maxN + 2; k < length; k++) {
				capStarts[0][k] = position + maxN + 2;
				capSizes[0][k] = length - position - maxN - 2;
			}
			if (position == 0)
				segmentInfo[0] = 1;
			else
				segmentInfo[0] = 0;
			if (position == maxN - 2)
				segmentInfo[1] = length - 2;
			else
				segmentInfo[1] = length - 1;
			totalWork = 0;
			long positionResults = recurse(maxN - 1, false);
			synchronized (memo){
				results += positionResults;
				globalWork += totalWork;
				
				System.out.println(position + " " + memo.size() + " " + totalWork + " " + globalWork + " " + positionResults + " " + results);
			}
			digits[position] = 0;
			digits[position + maxN + 1] = 0;
			digitsOnTop[position] = 0;
			digitsOnTop[position + maxN + 1] = 0;
			
		}
		
		
	}
	
	/**
	 * 
	 * @param n The next number we are trying to place in the sequence.
	 * 			Numbers are always placed in descending order.
	 * @param twoSided 	If this is true, for some number greater than n, it was possible to add that
	 * 					number to the sequence with its joining line either above or below the line.
	 * 					Therefore we need to find all the solutions for both cases, and only count the
	 * 					number of distinct ones. We cannot get the number of solutions
	 * 					from the cache in this scenario. When twoSided is true, we store the solutions 
	 * 					as the keys to a MultiLongLongMap, stored in the sequences variable. The number of
	 * 					distinct sequences is thus the size of the map.
	 * @return The number of possible sequences, given the state of the digits array.
	 */
	private long recurse(final int n, final boolean twoSided) {
		boolean twoSidedHere = twoSided;
		work[n]++;
		totalWork++;
		
		
		if (n == 0){
//				System.out.println(sequences.size());
//				System.out.println(Arrays.toString(digits));
			if (twoSided) {
				
				long[] key = new long[8];
				for (int i = 0; i < digits.length; i++) 
					key[i/10] |= ((long) digits[i]) << (6 * (i % 10));
				
				sequences.put(key, 1);
			}
			return 1;
		}
		
		long cacheValue;
		long[] cacheKey = null;
		
		boolean inTheWall = true;
		int filledOnHold = 0;
		
		if (n>=minMemoLevel && n<=maxMemoLevel){
			//We are going to use the cache
			int segmentCount = 0;
			int wallCount = 0;
			boolean emptySegment = true;
			int gaps = 0;
			//Find out where the segments are
			for (int i = 0; i < length; i++) {
				
				if (digits[i] == 0){
					if (inTheWall) {
						inTheWall = false;
						segmentCount++;
						segmentStart[wallCount] = i;
					}
					filledOnHold = 0;
					gaps++;
				}
				else if (!inTheWall) {
					if ((i < length - 1) && digits[i + 1] > 0
								&& (digitsOnTop[i] > 0 && digitsOnTop[i + 1] == 0
										|| digitsOnTop[i] == 0 && digitsOnTop[i + 1] > 0)){
						//We have hit a wall
						inTheWall = true;
						segmentEnd[wallCount] = i - filledOnHold;
						if ((gaps & 1) == 1)
							return 0;
						segmentGaps[wallCount] = gaps;
						gaps = 0;
						segmentEmpty[wallCount] = emptySegment;
						filledOnHold = 0;
						emptySegment = true;
						wallCount++;
					}
					else {
						filledOnHold++;
						if (emptySegment)
							emptySegment = false;
					}
				}

			}
			
			if (segmentCount > wallCount) {
				segmentEnd[wallCount] = length - filledOnHold;
				segmentEmpty[wallCount] = emptySegment;
				segmentGaps[wallCount] = gaps;
			}
			
			//Work out whether the segments should be cached in forward or reverse
			for (int i = 0; i < segmentCount; i++) {
				boolean reverseOrder = false;
				boolean determined = false;
				int startPos = segmentStart[i];
				int endPos = segmentEnd[i];
				int segmentLength = endPos - startPos;
				if (segmentLength < 3)
					return 0;
				if (!segmentEmpty[i]) {
					for (int j = 0; j < segmentLength; j++)
						if (digits[startPos + j] > 0 && digits[endPos - j - 1] == 0) {
							reverseOrder = true;
							determined = true;
							break;
						}
						else if (digits[startPos + j] == 0 && digits[endPos - j - 1] > 0) {
							determined = true;
							break;
						}
					
					if (!determined) //use digitsOnTop as a tie-breaker
						for (int j = 0; j < segmentLength; j++)
							if (digitsOnTop[startPos + j] > 0 && digitsOnTop[endPos - j - 1] == 0) {
								reverseOrder = true;
								break;
							}
							else if (digitsOnTop[startPos + j] == 0 && digitsOnTop[endPos - j - 1] > 0) 
								break;
				}
				segmentPositions[i] = i;
				segmentReverse[i] = reverseOrder;
				segmentSort[i] = segmentLength * 256 + segmentGaps[i];
			}
			
			//Sort the segments
			
			if (segmentCount > 1) {
				for (int j = 0; j < segmentCount -1; j++) {
					boolean sorted = true; 
					for (int i = 0; i < segmentCount - 1; i++) {
						int segmentNumber1 = segmentPositions[i];
						int segmentNumber2 = segmentPositions[i + 1];
						if (segmentSort[segmentNumber1] > segmentSort[segmentNumber2]) {
							int temp = segmentPositions[i + 1];
							segmentPositions[i + 1] = segmentPositions[i];
							segmentPositions[i] = temp;
							sorted = false;
						}
					}
					if (sorted)
						break;
				}
			}
			
			//Build the key

			cacheKey = new long[4];
			boolean topSegment = true; //Records whether the first digit in the segment is on the top
			int position = 0;
			for (int i = 0; i < segmentCount; i++) {
				
				if (i > 0) {//Build a wall
					cacheKey[position / 64] |= powers[position % 64];
					cacheKey[2 + (position / 64)] |= powers[position % 64];
					position++;
					cacheKey[position / 64] |= powers[position % 64];
					position++;
				}
				int segmentNumber = segmentPositions[i];
				int startPos = segmentStart[segmentNumber];
				int endPos = segmentEnd[segmentNumber];
				boolean reverseOrder = segmentReverse[segmentNumber];
				boolean emptySoFar = true;
				if (reverseOrder)
					for (int j = endPos - 1; j >= startPos; j--) {
						if (digits[j] > 0) {
							if (emptySoFar) {
								topSegment = (digitsOnTop[j] > 0);
								emptySoFar = false;
							}
							cacheKey[position / 64] |= powers[position % 64];
							if (topSegment == (digitsOnTop[j] > 0))
								cacheKey[2 + (position / 64)] |= powers[position % 64];
						}
						position++;
					}
				else for (int j = startPos; j < endPos; j++) {
					if (digits[j] > 0) {
						if (emptySoFar) {
							topSegment = (digitsOnTop[j] > 0);
							emptySoFar = false;
						}
						cacheKey[position / 64] |= powers[position % 64];
						if (topSegment == (digitsOnTop[j] > 0))
							cacheKey[2 + (position / 64)] |= powers[position % 64];
					}
					position++;
				}
			}
			
			while (position < length) {
				cacheKey[position / 64] |= powers[position % 64];
				position++;
			}
			
			if (!twoSided) {
				cacheValue = memo.get(cacheKey);
				if (cacheValue != -1) {
					cacheHits++;
					cacheHitValues[wallCount]++;
//					System.out.println(n + Arrays.toString(cacheKey) + cacheValue);
					return cacheValue;
				}
				
			}
			
		}
		
		/*
		 * Look for incompatible assertions about digits required in segments. For example
		 * if two segments both require a 1, no solution is possible.
		 */
		int requiredDigitsSum = 0;
		int requiredDigitsOr = 0;
		int requiredSegmentIndex = 0;
		do {
			final int requiredValue = segmentInfo[requiredSegmentIndex + 2];
			if (requiredValue != 0) {
				requiredDigitsSum += requiredValue;
				requiredDigitsOr |= requiredValue;
				if (requiredDigitsSum != requiredDigitsOr) {
						if (cacheKey != null) 
							memo.put(cacheKey, 0);
						return 0;
				}
			}
			int firstGap = segmentInfo[requiredSegmentIndex];
			int oldFirstGap = firstGap;
			requiredSegmentIndex = segmentInfo[requiredSegmentIndex + 4];
			firstGap = segmentInfo[requiredSegmentIndex];
			
			while (oldFirstGap == firstGap && requiredSegmentIndex > 0) {
				oldFirstGap = firstGap;
				requiredSegmentIndex = segmentInfo[requiredSegmentIndex + 4];
				firstGap = segmentInfo[requiredSegmentIndex];
			}
			
		} while (requiredSegmentIndex > 0);
		
		/*
		 * Now we try to place n in the sequence, iterating over the segments
		 */
		long counter = 0;
		int segmentIndex = 0;
		segmentIndex = 0;
		do {
			int firstGap = segmentInfo[segmentIndex];
			if (firstGap == -1) {
				segmentIndex = segmentInfo[segmentIndex + 4];
				continue;
			}
			final int lastGap = segmentInfo[segmentIndex + 1];
			final int nextSegmentIndex = segmentInfo[segmentIndex + 4];
			final int oldRequired = segmentInfo[segmentIndex + 2];
			final int oldPrevious = segmentInfo[segmentIndex + 3];
			for (int i = firstGap; i < lastGap - n; i++){
				if (digits[i] == 0 && digits[i + n + 1] == 0){
					digits[i] = n;
					digits[i + n + 1] = n;
					for (int j = 0; j < 2; j++) {
						if (caps[j][i] != caps[j][i + n + 1])
							continue;
						if (j == 0) {
							digitsOnTop[i] = n;
							digitsOnTop[i + n + 1] = n;
						}
						
						boolean failHere = false;
						int currentSegmentEndPos = lastGap;
						boolean newSegmentAtStart = false;
						boolean newSegmentAtEnd = false;
						if (!failHere && (digits[i + 1] > 0 && (j == 0) == (digitsOnTop[i + 1] == 0)
							|| i > (firstGap + 1) && digits[i - 1] > 0 && (j == 0) == (digitsOnTop[i - 1] == 0))) {//New segment
							currentSegmentEndPos = i;
							newSegmentAtStart = true;
						}
						if (!failHere && (i < lastGap - n - 1) &&
								(digits[i + n + 2] > 0 && (j == 0) == (digitsOnTop[i + n + 2] == 0)
								|| digits[i + n] > 0 && (j == 0) == (digitsOnTop[i + n] == 0))) {//New segment
							newSegmentAtEnd = true;
							currentSegmentEndPos = currentSegmentEndPos == lastGap ? i + n + 1 : i;
						}
						failHere = processSegment(segmentIndex, firstGap, currentSegmentEndPos, nextSegmentIndex, n);
						if (!failHere && newSegmentAtStart)
							failHere = processSegment(i * 5, i, newSegmentAtEnd ? i + n + 1: lastGap, nextSegmentIndex, n);
						if (!failHere && newSegmentAtEnd)
							failHere = processSegment((i + n + 1) * 5, i + n + 1, lastGap, nextSegmentIndex, n);
						
						if (j == 0 && caps[1][i] == caps[1][i + n + 1] && !twoSided) { //double-sided
							twoSidedHere = true;
							sequences = new MultiLongLongMap(10, 1200, 8, true);
						}
						final int oldCap = caps[j][i];
						final int oldCapStart = capStarts[j][i];
						final int oldCapSize = capSizes[j][i];
						long counted = 0;

							for (int k = i + 1; k < i + n + 1; k++) {
								caps[j][k] = n;
								capStarts[j][k] = i + 1;
								capSizes[j][k] = n;
							}
							int newCapSize = i - oldCapStart;
							for (int k = oldCapStart; k < i; k++) {
								capSizes[j][k] = newCapSize;
							}
		
							newCapSize = oldCapStart + oldCapSize - i - n - 2;
							for (int k = i + n + 2; k < oldCapStart + oldCapSize; k++) {
								capStarts[j][k] = i + n + 2;
								capSizes[j][k] = newCapSize;
							}
						
						if (!failHere) {
							counted = recurse(n - 1, twoSidedHere);

						}
	
						
						if (twoSidedHere) {
							if (j == 1) {
								counter += sequences.size();
								twoSidedHere = twoSided;
							}
						}
						else
							counter += counted;
						
						for (int k = i + 1; k < i + n + 1; k++)
							caps[j][k] = oldCap;
						for (int k = oldCapStart; k < oldCapStart + oldCapSize; k++) {
							capStarts[j][k] = oldCapStart;
							capSizes[j][k] = oldCapSize;
						}
						segmentInfo[segmentIndex] = firstGap;
						segmentInfo[segmentIndex + 1] = lastGap;
						segmentInfo[segmentIndex + 2] = oldRequired;
						segmentInfo[segmentIndex + 3] = oldPrevious;
						segmentInfo[segmentIndex + 4] = nextSegmentIndex;
						segmentInfo[nextSegmentIndex + 3] = segmentIndex;
						if (j == 0) {
							digitsOnTop[i] = 0;
							digitsOnTop[i + n + 1] = 0;
						}
					}
					digits[i] = 0;
					digits[i + n + 1] = 0;
				}
			}
			
			int oldFirstGap = firstGap;
			segmentIndex = segmentInfo[segmentIndex + 4];
			firstGap = segmentInfo[segmentIndex];
			while (oldFirstGap == firstGap && segmentIndex > 0) {
				oldFirstGap = firstGap;
				segmentIndex = segmentInfo[segmentIndex + 4];
				firstGap = segmentInfo[segmentIndex];
			}
			
		} while (segmentIndex > 0);
		
		if (n>=minMemoLevel && !twoSided && n<=maxMemoLevel){
			memo.put(cacheKey, counter);
		}
		
		
		return counter;
		
	}

	private boolean processSegment(final int segmentIndex, final int startPos, final int endPos, final int nextSegmentIndex, final int n) {

		boolean failure = false;
		//Look for a wall

		int gaps = 0;
		boolean inTheWall = true;
		int requiredKey = 1;
		int filledOnHold = 0;
		int firstGap = -1;
		int lastGap = -1;
		int requiredValue = 0;
		for (int i = startPos; i < endPos + 1; i++){
			if (digits[i] == 0){
					if (filledOnHold >= n && (gaps & 1) == 1) {
						failure = true;
						break;
					}
					gaps++;
					lastGap = i;
					if (inTheWall) {
						requiredKey = 1;
						inTheWall = false;
						firstGap = i;
					}
					while (filledOnHold > 0 && requiredKey < 1024) {
						requiredKey <<= 1;
						requiredKey++;
						filledOnHold--;
					}
					if (requiredKey < 1024)
						requiredKey <<= 1;
					filledOnHold = 0;
			}
			if (digits[i] > 0 || i == endPos) {
				if (	i == endPos
						|| digits[i + 1] > 0
							&& (digitsOnTop[i] > 0 && digitsOnTop[i + 1] == 0
									|| digitsOnTop[i] == 0 && digitsOnTop[i + 1] > 0)){
					//We have hit a wall
					if (!inTheWall) {
						if ((gaps & 1) == 1) {
							failure = true;
							break;
						}
						inTheWall = true;
						if (requiredKey < 1024)
							requiredValue = required[requiredKey];
						if (requiredValue < 0) {
							failure = true;
							break;
						}
						break;
					}
					
				}
				else if (!inTheWall){
					filledOnHold++;
				}
			}
		 
		}
		
		int previousSegmentIndex = segmentInfo[nextSegmentIndex + 3];
		
		segmentInfo[segmentIndex] = firstGap;
		segmentInfo[segmentIndex + 1] = lastGap;
		segmentInfo[segmentIndex + 2] = requiredValue;
		if (previousSegmentIndex != segmentIndex) {
			segmentInfo[previousSegmentIndex + 4] = segmentIndex;
			segmentInfo[nextSegmentIndex + 3] = segmentIndex;
			segmentInfo[segmentIndex + 3] = previousSegmentIndex;
			segmentInfo[segmentIndex + 4] = nextSegmentIndex;
		}
		
		return failure;
		
	}
	
}
