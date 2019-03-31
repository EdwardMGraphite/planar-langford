This program enumerates planar Langford sequences.

Langford sequences are arrangements of two instances of each of the numbers from 1 to n, such that there are exactly k numbers between the two instances of k, for all k from 1 to n. For example, for n = 3:

3 1 2 1 3 2

This solution is also planar, in a sense introduced by Donald Knuth in the Volume 4 of The Art of Computer Programming, because we can draw lines as follows:

 _______
|  ___  |
| |   | |
3 1 2 1 3 2
    |_____|
	
The connecting lines must lie wholly above or below the line rather than looping around the end.

The number of sequences for various n is in the On-line Encyclopedia of Integer Sequences at https://oeis.org/A125762.

The algorithm proceeds in a conventional way, placing the largest pair of digits first, and continuing in descending order. Adding the numbers in descending order enables various ways of pruning the search tree, as well as having the general merit, it would seem, that the largest remaining number will tend to have fewer spaces to fit in the sequence.

Tree Pruning

Suppose that the number i has been placed as below:

       _________________
      |                 |
      |                 |
_ _ _ i _ _ _ ... _ _ _ i _ _ _ _ 


Clearly it is impossible for any number j to be positioned with one instance inside the 'cap' connecting the i pair, and one instance outside, but because any number j, yet to be placed, will be less than i, it is also impossible for one j to be positioned to the left of this cap and the other to the right. In other words, any future pairs of numbers positioned with their caps on a given side of the line, must not have any other numbers between them, also with caps on that side of the line. It follows that if we have the following situation:

            |
            |
... _ _ _ _ i j _ _ ...
              |
		      |
				
i.e. two adjacent numbers with their caps on opposite sides of the line, they create an impenetrable barrier, which no future caps may cross. We can call these barriers 'walls'. They partition the whole sequence into 'segments'.

It follows that when a number is placed, both instances must fall within the same existing segment, possibly creating one or two new segments in the process. Since both members of any pair of numbers must be placed in the same segment, if a segment is ever created with an odd number of empty spaces, we can immediately conclude that no solution is possible, and retreat back up the search tree.

Suppose that a segment looks like this:

_ _ _ _ _  * * * * * * * * * * _ _ _ _ _ _ _

where the ten asterisks represent filled spaces. Note that there are an odd number of gaps either side of the filled section. Therefore any solution must contain at least one number that bridges this gap, i.e. is greater than or equal to 10. If 10 has already been placed, we know that no solution is possible.


Suppose that a segment looks like this:

_ * _ * _ _ * _ _


It turns out that there is no planar solution to this pattern, using any numbers, regardless of which side of the line the filled spaces' caps lie. Therefore we can conclude that any sequence containing a segment with this pattern, is insoluble. There are relatively few insoluble patterns, but there is another trick we can use. For example, a segment with the following pattern:

_ * * _ _ _ _ _ 

could have one of three possible planar solutions:

5 * * 1 2 1 5 2
4 * * 3 1 4 1 3
3 * * 2 3 1 2 1

Therefore, we can say with certainty that it must contain a 1. If our sequence contains another segment of which we can make the same assertion, then we know that no solution is possible. The program contains an array mapping these segment patterns to a binary value representing the numbers we can assert that the segment must contain in any solution. For example, if it must contain 1, 4, and 6, the binary value is 41 = 1 + 8 + 32. The program both adds the values for each segment, and combines them with bitwise OR. If the results are different then there must be a duplicated required number, so no solution is possible.

There is another, similar, class of assertion we can make. For example, any solution to the segment pattern above, must contain any two of the numbers, 1, 2, and 3. If there is another segment of which the same is true, then again, no solution is possible. The program includes assertions about having two of 1,2, and 3, represented by the 1024 bit, two of 1, 2, and 4, represented by the 2048 bit, two of 1, 3, and 4, represented by the 4096 bit, and two of 2, 3, and 4, represented by the 8192 bit. In all, the program includes hand-calculated assertions representing 121 patterns, up to and including those with a total of nine filled and unfilled spaces. It could be that writing a program to calculate assertions for longer patterns would speed up the algorithm by a reasonable factor.

Unused Tree Pruning Methods

Some other methods of detecting insoluble sequences appear to have benefits that are outweighed by the computational cost. For example, consider a segment with the following pattern:

_ _ _ _ _   _ * _ _ _   _ _ _
          *           *

Here asterisks above and below the line represent numbers with their caps on the corresponding side. There are twelve empty spaces, so six pairs are required, but the largest number that could be fitted in this pattern is 5. Therefore, no solution is possible. Similarly, if a pattern with twelve empty spaces could accommodate a pair of 6s, we could conclude that it must include all the numbers from 1 to 6. If we can assert that any other segment must contain any one of these digits, no solution would be possible. This sort of argument can be extended to multiple segments. If the largest individual number that any one of them could contain, is less than half their total number of empty spaces, no solution is possible. If the maximum size of a number that a segment could contain, is one greater than half the total number of empty spaces, we can at least deduce that it must contain two of any set of three numbers between one and that maximum.

Caching

Consider these two partial solutions for N = 20:

 |  |                                        |  |
20 18 _ _ _ _ _ _ _ _ _  19 _ _ _ _ _ _ _ _ 18 20 _ _ _ _ _ _ _ _ _  19 _ _ _ _ _ _ _ _
                          |                                           |

 |  |                                        |  |
20 18 _ _ _ _ _ _ _ _ 19 _  _ _ _ _ _ _ _ _ 18 20 _ _ _ _ _ _ _ _ 19 _  _ _ _ _ _ _ _ _
                       |                                           |

Ignoring the two filled spaces at the start, and counting from the left, the first sequence has nine spaces, followed by one number with a cap on the bottom, then eight spaces, then two numbers with caps on the top, then nine spaces, then one number with a cap on the bottom, then eight spaces. The second sequence, counting from the right, has exactly the same pattern of filled and empty spaces. Therefore, any way of filling in the remaining numbers in the first sequence, will also fit into the second sequence, if its order is reversed. The program uses a cache of patterns of filled and empty spaces (taking into account which numbers have caps on which side of the line) in an attempt to save some time. Because constructing a key for the cache, and reading and writing to it,are relatively expensive operations, I only do this for the top fourteen levels of recursion. This also keeps the cache to a reasonable size, a few million entries.

To minimise the size of the cache, and increase the number of hits, the algorithm reduces each pattern to an almost-canonical form before adding it to the cache. This reduction uses the following ideas:

- Since all pairs of numbers must be placed within segments, the thickness of walls between segments is irrelevant. All walls are reduced to two spaces, one with a number capped above the line, one with a number capped below, i.e. a minimal wall. All the spare filled spaces are moved to the end of the sequence.

- The first filled space with a segment is treated as if its number were capped above, and other filled spaces treated as capped above or below depending on how their actual cap position compares to that of the first number.

- Segments are sorted according to their length, with the number of empty spaces used as a tie-breaker. This imprecision in sorting is why the reduction is "almost-canonical": it is possible for two sequences that should have the same cache key, to end up with different ones.

- Each segment is used in either original or reverse order, depending on which orientation makes it smaller when considered as a binary number, 1 for a filled space, and 0 for empty.
