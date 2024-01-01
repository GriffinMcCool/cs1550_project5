//author Griffin McCool

import java.io.*;
import java.util.Queue;
import java.util.LinkedList;

public class vmsim {

	private class PTE {
		int index;
		int frame;
		boolean dirty;
		boolean referenced;
		boolean valid;

		//constructor
		public PTE(){
			this.index = -1;
			this.frame = -1;
			this.dirty = false;
			this.referenced = false;
			this.valid = false;
		}

		//copy constructor
		public PTE(int ind){
			this.index = ind;
			this.frame = -1;
			this.dirty = false;
			this.referenced = false;
			this.valid = false;
		}
	}

	private class leaf {
	//each leaf has 1024 PTE's
	PTE[] entries;
	int validEntries;

		public leaf(int l1Index){
			this.entries = new PTE[1024];
			//fill with PTE's
			for (int i = 0; i < 1024; i++){
				//initialize with index = l1Index << 10 | l2Index (i)
				this.entries[i] = new PTE((l1Index << 10 ) | i);
			}
			this.validEntries = 0;
		}

	}


	public static void main(String[] args){
		vmsim m = new vmsim();

		//check if there are correct number of args
		if (args.length != 5) m.printUsage();
		int numFrames = 0;
		String alg, fileName;

		if (!args[0].equals("-n")){
			m.printUsage();
		}

		//get numFrames from args
		try {
			numFrames = Integer.parseInt(args[1]);
		} catch (Exception e){
			System.out.println("Number of frames must be an integer.");
			m.printUsage();
		}
		if (!args[2].equals("-a")){
			m.printUsage();
		}
		//get algorithm from args
		alg = args[3];
		if (!alg.equals("opt") && !alg.equals("clock") && !alg.equals("lru")){
			System.out.println("Algorithm must be \"opt\", \"clock\", or \"lru\".");
			m.printUsage();
		}

		//get fileName from args
		fileName = args[4];

		if (alg.equals("opt")) m.opt(numFrames, fileName);
		else if (alg.equals("clock")) m.clock(numFrames, fileName);
		else m.lru(numFrames, fileName);
	}

	private void opt(int numFrames, String fileName){
		int totalMemAccesses = 0, totalPageFaults = 0, totalWrites = 0, numLeaves = 0, totalSize = 0;
		BufferedReader br = null;
		//represents which pages are in ram
		PTE[] ram = new PTE[numFrames];

		//create BufferedReader to read trace
		try{
			//File file = new File(fileName);
			br = new BufferedReader(new FileReader(fileName));
		} catch (Exception e){
			System.out.println(fileName + " not found.");
			System.exit(0);
		}

		//initialize root, holds 512 leaves
		leaf[] pageTableRoot = new leaf[512];

		//create array (with size equal to the worst case number of pages = 2^19) of queues that allow us to store when each
		//page is accessed, which prevents us from having to search the entire trace for the latest used page. Instead, we
		//can just check the indexes in the array of the pages in RAM, and see which one is used last.
		Queue<Integer>[] future = new Queue[524288];
		for (int i = 0; i < 524288; i++){
			future[i] = new LinkedList<Integer>();
		}
		
		try{
			int lineNum = 1, index;
			String line;
			char start;
			//fill up future array
			while((line = br.readLine()) != null){
				//remove spaces at start of string
				line = line.replaceFirst("^\\s*", "");

				//if line doesn't start with I, S, L, or M, it is not a valid line
				start = line.charAt(0);
				if (start == 'I' || start == 'S' || start == 'L' || start == 'M'){
					//get hex number and store into int. Since the first 9 bits are the level 1 index, the next 10 are the level 2 index,
					//and the last 13 are the offset, we can exclude the last 13 bits. We are left with just the index for the future array
					if (start == 'I') index = Integer.parseUnsignedInt(line.substring(3, 11), 16) >>> 13;
					else index = Integer.parseUnsignedInt(line.substring(2, 10), 16) >>> 13;
					future[index].add(lineNum);
				}
				lineNum++;
			}

			try{
				br = new BufferedReader(new FileReader(fileName));
			} catch (Exception e){
				System.out.println(fileName + " not found.");
				System.exit(0);
			}

			int l1Index, l2Index, startSub, endSub, filledFrames = 0;
			//implementation of opt algorithm
			while((line = br.readLine()) != null){
				//remove spaces at start of string
				line = line.replaceFirst("^\\s*", "");

				//if line doesn't start with I, S, L, or M, it is not a valid line
				start = line.charAt(0);
				if (start == 'I' || start == 'S' || start == 'L' || start == 'M'){
					totalMemAccesses++;
					//add another memory access if the instruction is a modify (load and store)
					if (start == 'M') totalMemAccesses++;
					//get hex number and store into int. Since the first 9 bits are the level 1 index, the next 10 are the level 2 index,
					//and the last 13 are the offset, we must extract the level 1 and level 2 index through bitwise operations
					if (start == 'I'){
						startSub = 3;
						endSub = 11;
					} else {
						startSub = 2;
						endSub = 10;
					}
					//no need to mask, can just right shift by number of offest + level 2 index bits
					l1Index = Integer.parseUnsignedInt(line.substring(startSub, endSub), 16) >>> 23;
					//& to mask and leave middle 10 bits, then right shift by number of offset bits
					l2Index = (Integer.parseUnsignedInt(line.substring(startSub, endSub), 16) & 8380416) >>> 13;

					//check if leaf exists, if not create one
					if (pageTableRoot[l1Index] == null){
						pageTableRoot[l1Index] = new leaf(l1Index);
						numLeaves++;
					}

					//check if PTE is valid (already in RAM)
					PTE entry = pageTableRoot[l1Index].entries[l2Index];
					if (entry.valid == true){
						//no page fault
						//if page is modified (modify or store), set dirty = true
						if (start == 'M' || start == 'S'){
							entry.dirty = true;
						}
						//set referenced = true
						entry.referenced = true;
						//remove this access from future queue
						future[entry.index].remove();
						System.out.println("hit");
					} else {
						//page fault, must put into RAM
						totalPageFaults++;
						//if there is an empty spot, just put the page in that frame
						if (filledFrames < numFrames){
							//turn indexes back into page number
							ram[filledFrames] = entry;
							//set frame number for entry
							entry.frame = filledFrames;
							filledFrames++;
							//set valid and referenced bits
							entry.valid = true;
							entry.referenced = true;
							//if page is modified (modify or store), set dirty = true
							if (start == 'M' || start == 'S'){
								entry.dirty = true;
							}
							//increment valid entries for this leaf
							pageTableRoot[l1Index].validEntries++;
							//remove this access from future queue
							future[entry.index].remove();
							System.out.println("page fault - no eviction");
						} else {
							int latest = -1, latestIndex = -1;
							PTE replaced = null;
							//go through each frame in RAM and find which page we will use last
							for (int x = 0; x < numFrames; x++){
								// if the queue is null, then that page isn't used again
								if (future[ram[x].index].peek() == null){
									latestIndex = x;
									replaced = ram[x];
									break;
								}
								if (future[ram[x].index].peek() > latest){
									latest = future[ram[x].index].peek();
									latestIndex = x;
									replaced = ram[x];
								}
							}
							//replace frame
							ram[latestIndex] = entry;
							entry.frame = latestIndex;
							//update values for newly added page
							entry.valid = true;
							entry.referenced = true;
							//if page is modified (modify or store), set dirty = true
							if (start == 'M' || start == 'S'){
								entry.dirty = true;
							}
							//increment valid entries for this leaf
							pageTableRoot[l1Index].validEntries++;
							//remove this access from future queue
							future[entry.index].remove();
							//write old page back if it is dirty, update valid, and update frame num
							replaced.valid = false;
							if (replaced.dirty == true){
								totalWrites++;
								replaced.dirty = false;
								System.out.println("page fault - evict dirty");
							} else {
								System.out.println("page fault - evict clean");
							}
							replaced.frame = -1;
							//decrement valid entries for replaced page's leaf
							pageTableRoot[replaced.index >>> 10].validEntries--;
							//if there are now 0 valid entries for that leaf, we can delete it to save space
							if (pageTableRoot[replaced.index >>> 10].validEntries == 0){
								pageTableRoot[replaced.index >>> 10] = null;
								numLeaves--;
							}
						}
					}

				}
			}

		} catch (IOException e){
			;
		}
		//total size = ( (sizeof(root) * 8 bytes (per reference to a leaf) ) + (numleaves * sizeof(leaf) * 4 bytes (per PTE) ) )
		totalSize = (512 * 8) + (numLeaves * 1024 * 4);
		printStats("opt", numFrames, totalMemAccesses, totalPageFaults, totalWrites, numLeaves, totalSize);
	}

	private void clock(int numFrames, String fileName){
		int totalMemAccesses = 0, totalPageFaults = 0, totalWrites = 0, numLeaves = 0, totalSize = 0;
		BufferedReader br = null;
		//represents which pages are in ram
		PTE[] ram = new PTE[numFrames];

		//create BufferedReader to read trace
		try{
			//File file = new File(fileName);
			br = new BufferedReader(new FileReader(fileName));
		} catch (Exception e){
			System.out.println(fileName + " not found.");
			System.exit(0);
		}

		//initialize root, holds 512 leaves
		leaf[] pageTableRoot = new leaf[512];

		try{
			int l1Index, l2Index, startSub, endSub, filledFrames = 0, clockPos = 0;
			String line;
			char start;
			//implementation of clock algorithm
			while((line = br.readLine()) != null){
				//remove spaces at start of string
				line = line.replaceFirst("^\\s*", "");

				//if line doesn't start with I, S, L, or M, it is not a valid line
				start = line.charAt(0);
				if (start == 'I' || start == 'S' || start == 'L' || start == 'M'){
					totalMemAccesses++;
					//add another memory access if the instruction is a modify (load and store)
					if (start == 'M') totalMemAccesses++;
					//get hex number and store into int. Since the first 9 bits are the level 1 index, the next 10 are the level 2 index,
					//and the last 13 are the offset, we must extract the level 1 and level 2 index through bitwise operations
					if (start == 'I'){
						startSub = 3;
						endSub = 11;
					} else {
						startSub = 2;
						endSub = 10;
					}
					//no need to mask, can just right shift by number of offest + level 2 index bits
					l1Index = Integer.parseUnsignedInt(line.substring(startSub, endSub), 16) >>> 23;
					//& to mask and leave middle 10 bits, then right shift by number of offset bits
					l2Index = (Integer.parseUnsignedInt(line.substring(startSub, endSub), 16) & 8380416) >>> 13;

					//check if leaf exists, if not create one
					if (pageTableRoot[l1Index] == null){
						pageTableRoot[l1Index] = new leaf(l1Index);
						numLeaves++;
					}

					//check if PTE is valid (already in RAM)
					PTE entry = pageTableRoot[l1Index].entries[l2Index];
					if (entry.valid == true){
						//no page fault
						//if page is modified (modify or store), set dirty = true
						if (start == 'M' || start == 'S'){
							entry.dirty = true;
						}
						//set referenced = true
						entry.referenced = true;
						System.out.println("hit");
					} else {
						//page fault, must put into RAM
						totalPageFaults++;
						//if there is an empty spot, just put the page in that frame
						if (filledFrames < numFrames){
							//turn indexes back into page number
							ram[filledFrames] = entry;
							//set frame number for entry
							entry.frame = filledFrames;
							filledFrames++;
							//set valid and referenced bits
							entry.valid = true;
							entry.referenced = true;
							//if page is modified (modify or store), set dirty = true
							if (start == 'M' || start == 'S'){
								entry.dirty = true;
							}
							//increment valid entries for this leaf
							pageTableRoot[l1Index].validEntries++;
							System.out.println("page fault - no eviction");
						} else {
							int replaceIndex = -1;
							PTE replaced = null;

							//cycle through clock
							while(ram[clockPos].referenced == true){
								//set current page's referenced bit to false
								ram[clockPos].referenced = false;
								//update clockPos
								clockPos++;
								//if clock reaches end, reset to index 0
								if(clockPos >= numFrames) clockPos = 0;
							}
							//breaks out of loop once referenced is false, so we have the correct index
							replaceIndex = clockPos;
							replaced = ram[clockPos];

							//replace frame
							ram[replaceIndex] = entry;
							entry.frame = replaceIndex;
							//update values for newly added page
							entry.valid = true;
							entry.referenced = true;
							//if page is modified (modify or store), set dirty = true
							if (start == 'M' || start == 'S'){
								entry.dirty = true;
							}
							//increment valid entries for this leaf
							pageTableRoot[l1Index].validEntries++;
							//write old page back if it is dirty, update valid, and update frame num
							replaced.valid = false;
							if (replaced.dirty == true){
								totalWrites++;
								replaced.dirty = false;
								System.out.println("page fault - evict dirty");
							} else {
								System.out.println("page fault - evict clean");
							}
							replaced.frame = -1;
							//decrement valid entries for replaced page's leaf
							pageTableRoot[replaced.index >>> 10].validEntries--;
							//if there are now 0 valid entries for that leaf, we can delete it to save space
							if (pageTableRoot[replaced.index >>> 10].validEntries == 0){
								pageTableRoot[replaced.index >>> 10] = null;
								numLeaves--;
							}
						}
					}	
				}
			}
		} catch (IOException e){
			;
		}
		//total size = ( (sizeof(root) * 8 bytes (per reference to a leaf) ) + (numleaves * sizeof(leaf) * 4 bytes (per PTE) ) )
		totalSize = (512 * 8) + (numLeaves * 1024 * 4);
		printStats("clock", numFrames, totalMemAccesses, totalPageFaults, totalWrites, numLeaves, totalSize);
	}

	private void lru(int numFrames, String fileName){
		int totalMemAccesses = 0, totalPageFaults = 0, totalWrites = 0, numLeaves = 0, totalSize = 0;
		BufferedReader br = null;
		//represents which pages are in ram
		PTE[] ram = new PTE[numFrames];
		//LinkedList to maintain most/least recently used
		LinkedList<PTE> q = new LinkedList<PTE>();

		//create BufferedReader to read trace
		try{
			br = new BufferedReader(new FileReader(fileName));
		} catch (Exception e){
			System.out.println(fileName + " not found.");
			System.exit(0);
		}

		//initialize root, holds 512 leaves
		leaf[] pageTableRoot = new leaf[512];

		try{
			int l1Index, l2Index, startSub, endSub, filledFrames = 0;
			String line;
			char start;
			//implementation of lru algorithm
			while((line = br.readLine()) != null){
				//remove spaces at start of string
				line = line.replaceFirst("^\\s*", "");

				//if line doesn't start with I, S, L, or M, it is not a valid line
				start = line.charAt(0);
				if (start == 'I' || start == 'S' || start == 'L' || start == 'M'){
					totalMemAccesses++;
					//add another memory access if the instruction is a modify (load and store)
					if (start == 'M') totalMemAccesses++;
					//get hex number and store into int. Since the first 9 bits are the level 1 index, the next 10 are the level 2 index,
					//and the last 13 are the offset, we must extract the level 1 and level 2 index through bitwise operations
					if (start == 'I'){
						startSub = 3;
						endSub = 11;
					} else {
						startSub = 2;
						endSub = 10;
					}
					//no need to mask, can just right shift by number of offest + level 2 index bits
					l1Index = Integer.parseUnsignedInt(line.substring(startSub, endSub), 16) >>> 23;
					//& to mask and leave middle 10 bits, then right shift by number of offset bits
					l2Index = (Integer.parseUnsignedInt(line.substring(startSub, endSub), 16) & 8380416) >>> 13;

					//check if leaf exists, if not create one
					if (pageTableRoot[l1Index] == null){
						pageTableRoot[l1Index] = new leaf(l1Index);
						numLeaves++;
					}

					//check if PTE is valid (already in RAM)
					PTE entry = pageTableRoot[l1Index].entries[l2Index];

					if (entry.valid == true){
						//no page fault
						//if page is modified (modify or store), set dirty = true
						if (start == 'M' || start == 'S'){
							entry.dirty = true;
						}
						//set referenced = true
						entry.referenced = true;
						//remove from position in queue and add to back
						q.remove(entry);
						q.add(entry);
						System.out.println("hit");
					} else {
						//page fault, must put into RAM
						totalPageFaults++;
						//if there is an empty spot, just put the page in that frame
						if (filledFrames < numFrames){
							//turn indexes back into page number
							ram[filledFrames] = entry;
							//set frame number for entry
							entry.frame = filledFrames;
							filledFrames++;
							//set valid and referenced bits
							entry.valid = true;
							entry.referenced = true;
							//if page is modified (modify or store), set dirty = true
							if (start == 'M' || start == 'S'){
								entry.dirty = true;
							}
							//increment valid entries for this leaf
							pageTableRoot[l1Index].validEntries++;
							//add entry to back of q (most recently used)
							q.add(entry);
							System.out.println("page fault - no eviction");
						} else {
							int replaceIndex;
							PTE replaced = null;
							//go to front of queue and remove
							replaced = q.remove();
							replaceIndex = replaced.frame;

							//replace frame
							ram[replaceIndex] = entry;
							entry.frame = replaceIndex;
							//update values for newly added page
							entry.valid = true;
							entry.referenced = true;
							//if page is modified (modify or store), set dirty = true
							if (start == 'M' || start == 'S'){
								entry.dirty = true;
							}
							//increment valid entries for this leaf
							pageTableRoot[l1Index].validEntries++;
							//add entry to back of q (most recently used)
							q.add(entry);
							//write old page back if it is dirty, update valid, and update frame num
							replaced.valid = false;
							if (replaced.dirty == true){
								totalWrites++;
								replaced.dirty = false;
								System.out.println("page fault - evict dirty");
							} else {
								System.out.println("page fault - evict clean");
							}
							replaced.frame = -1;
							//decrement valid entries for replaced page's leaf
							pageTableRoot[replaced.index >>> 10].validEntries--;
							//if there are now 0 valid entries for that leaf, we can delete it to save space
							if (pageTableRoot[replaced.index >>> 10].validEntries == 0){
								pageTableRoot[replaced.index >>> 10] = null;
								numLeaves--;
							}
						}
					}	
				}
			}
		} catch (IOException e){
			;
		}
		//total size = ( (sizeof(root) * 8 bytes (per reference to a leaf) ) + (numleaves * sizeof(leaf) * 4 bytes (per PTE) ) )
		totalSize = (512 * 8) + (numLeaves * 1024 * 4);
		printStats("lru", numFrames, totalMemAccesses, totalPageFaults, totalWrites, numLeaves, totalSize);
	}

	//print usage
	private void printUsage(){
		System.out.println("Usage: java vmsim -n <numframes> -a <opt|clock|lru> <tracefile>");
		System.exit(0);
	}

	//print the statistics out
	private void printStats(String alg, int numFrames, int totalMemAccesses, int totalPageFaults, int totalWrites, int numLeaves, int totalSize){
		System.out.println("Algorithm: " + alg);
		System.out.println("Number of frames: \t" + numFrames);
		System.out.println("Total memory accesses: \t" + totalMemAccesses);
		System.out.println("Total page faults: \t" + totalPageFaults);
		System.out.println("Total writes to disk: \t" + totalWrites);
		System.out.println("Number of page table leaves: \t" + numLeaves);
		System.out.println("Total size of page table: \t" + totalSize);
	}

}
