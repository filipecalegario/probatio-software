package main;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Vector;

import display.DisplayManager_v0_0_3;
import kinematic.KinematicCrank;
import mapper.MapperManager;
import model.Block;
import model.BlockFactory;
import model.BlockType;
import processing.core.PApplet;
import processing.serial.Serial; 

//Compatible with XS_Hub_v0_0_13
//Compatible with MockupSerial_v0.0.3
//Parsing one big line

public class Serial_Processing_libMapper_v0_0_9 extends PApplet {

	//private Signal out1;

	Serial myPort;        // The serial port
	//PrintWriter output;
	int xPos = 1;         // horizontal position of the graph
	float inByte = 0;
	float lastInByte = 0;
	float lastDerivate = 0;
	float energy = 0;

	MapperManager mapperManager;
	Vector<Block> blocks;
	DisplayManager_v0_0_3 display;
	KinematicCrank kinematic;

	boolean serialIsReady;
	
	int debug = BlockType.CRANK;

	public void settings() {  
		size(800, 600);
		pixelDensity(2);
		smooth();
	}

	public void setup () {
		long startTime = millis();
		serialIsReady = false;
		mapperManager = new MapperManager("probatio");
		blocks = new Vector<Block>();
		display = new DisplayManager_v0_0_3(this,6);
		kinematic = new KinematicCrank();
		mapperManager.printDeviceInitialization();

		//println(Serial.list());
		myPort = new Serial(this, Serial.list()[5], 115200);
		myPort.bufferUntil('\n');
		myPort.clear();
		println("Initializing serial port...");
		while (millis() - startTime < 500) {
			if (myPort.available() > 0) {
				myPort.readStringUntil('\n');
			}
		}
		println("Serial port ready!");
		serialIsReady = true;
		background(255);
	}

	public void draw () {

		mapperManager.pollDevice();
		display.updateDrawDisplaySlot();
		if(blocks.isEmpty()){
			display.cleanScreen();
		}
//		try {
//			for (int i = 0; i < blocks.size(); i++) {
//				Block currentBlock = blocks.elementAt(i);
//				if(millis() - currentBlock.getLastTimeUpdated() > 100){
//					removeBlockEvent(currentBlock);
//				}
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//		}

	}

	public void stop(){
		mapperManager.freeDevice();
	}

	public void serialEvent (Serial myPort) {
		if(serialIsReady){
			try {
				String inString = myPort.readStringUntil('\n');
				parseAndAddOrUpdate(inString);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	private void parseAndAddOrUpdate(String inString) {
		// 49;  2;   27;  179; 45;  1;   0;   50;  1;   0
		// [0]  [1]  [2]  [3]  [4]  [5]  [6]  [7]  [8]  [9]  [10]
		String subString = "";
		if(inString != null && inString.length() > 0){
			subString = inString.substring(0, inString.indexOf('\n')-1);
			String[] pieces = split(subString, ';');
			//System.out.println(Arrays.toString(pieces));
//			for (int i = 0; i < pieces.length; i++) {
//				if(pieces[i].equals("#")){
//					int id = Integer.parseInt(pieces[i+1]);
//					int quantity = Integer.parseInt(pieces[i+2]);
//					int[] values = new int[quantity];
//					for (int j = 0; j < values.length; j++) {
//						values[j] = Integer.parseInt(pieces[i+3+j]);
//					}
//					if(values[0] == -1){
//						removeBlockEvent(id);
//					} else {
//						
//					}
//					
//				}
//			}
			 
			Iterable<String> piecesIterable = Arrays.asList(pieces);
			Iterator<String> iterator = piecesIterable.iterator();
			while(iterator.hasNext()){
				if(iterator.next().equals("#")){
					int id = Integer.parseInt(iterator.next());
					int quantity = Integer.parseInt(iterator.next()); 
					int[] values = new int[quantity];
					for (int j = 0; j < values.length; j++) {
						values[j] = Integer.parseInt(iterator.next());
					}
					if(values[0] == -1){
						removeBlockEvent(id);
					} else {
						if(repositoryContainsBlock(id)){
							updateBlockEvent(id, values);
						} else {
							//Block newBlock = new Block(id, values, millis()); 
							Block newBlock = BlockFactory.createBlock(id, values, millis()); 
							addBlockEvent(newBlock);
						}						
					}
				}
			}
			
		}
	}
	
	

	private void addBlockEvent(Block block) {
		if (block != null) {
			if(!repositoryContainsBlock(block.getId())){
				System.out.println(block.getName() + " added");
				blocks.addElement(block);
				mapperManager.addSignal(block);
				for (int i = 0; i < block.getValues().length; i++) {
					display.addDisplaySlot(block.getId(), i, block.getValuesLabels()[i]);
					//TODO Exploring! Remove after!
					if(block.getId() == debug){
						display.addDisplaySlot(BlockType.NONE, 0, "Speed");
						display.addDisplaySlot(BlockType.NONE, 1, "Acceleration");
						kinematic.updateValue(0);
					}
				}
			}
		}
	}

	private void updateBlockEvent(int idBlock, int[] values) {
		Block block = getBlockById(idBlock);
		if (block != null) {
			block.updateValues(values, millis());
			mapperManager.updateSignal(block);
			for (int i = 0; i < block.getValues().length; i++) {
				display.updateValueDisplaySlot(block.getId(), i, values[i]);
				if(block.getId() == debug){
					kinematic.updateValue(values[i]);
					display.updateValueDisplaySlot(BlockType.NONE, 0, Math.round(kinematic.getAverageSpeed()));
					display.updateValueDisplaySlot(BlockType.NONE, 1, Math.round(kinematic.getAcceleration()));					
				}
			} 
		}
	}
	
	private void removeBlockEvent(int idBlock){
		Block block = getBlockById(idBlock);
		if (block != null) {
			removeBlockEvent(block);
		}
	}

	private void removeBlockEvent(Block block) {
		String name = block.getName();
		mapperManager.removeSignal(block);
		for (int i = 0; i < block.getValues().length; i++) {
			display.removeDisplaySlot(block.getId(), i);
			if(block.getId() == debug){
				display.removeDisplaySlot(BlockType.NONE, 0);
				display.removeDisplaySlot(BlockType.NONE, 1);				
			}
		}
		blocks.removeElement(block);
		System.out.println(name + " removed");
	}

	//TODO This is not cool!
	private boolean repositoryContainsBlock(int id) {
		boolean result = false;
		for (Block block : blocks) {
			if(block.getId() == id){
				result = true;
			}
		}
		return result;
	}

	//TODO Two walks in the array: This is not cool!
	private Block getBlockById(int id) {
		Block result = null;
		if(repositoryContainsBlock(id)){
			for (Block block : blocks) {
				if(block.getId() == id){
					result = block;
				}
			}
		}
		return result;
	}

	public void keyPressed() {
		mapperManager.freeDevice();
		exit();
	}

	static public void main(String[] passedArgs) {
		String[] appletArgs = new String[] {"main.Serial_Processing_libMapper_v0_0_9"};
		if (passedArgs != null) {
			PApplet.main(concat(appletArgs, passedArgs));
		} else {
			PApplet.main(appletArgs);
		}
	}

}
