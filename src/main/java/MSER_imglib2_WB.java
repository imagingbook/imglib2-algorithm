import java.awt.Color;
import java.io.File;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.io.Opener;
import ij.process.ImageProcessor;
import io.scif.img.IO;
import net.imglib2.algorithm.componenttree.mser.Mser;
import net.imglib2.algorithm.componenttree.mser.MserTree;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ByteImagePlus;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;

/**
 * Taken from imglib2-tutorials
 */
public class MSER_imglib2_WB {

	public <T extends NumericType<T> & NativeType<T>> MSER_imglib2_WB() {
		// define the file to open
//		File file = new File( "DrosophilaWing.tif" );
//		File file = new File("D://images/boats-tiny.png");
		File file = new File("D://images/boats.png");

		// open a file with ImageJ
		final ImagePlus imp = new Opener().openImage(file.getAbsolutePath());
		ByteImagePlus<UnsignedByteType> byteImagePlus = new ByteImagePlus<UnsignedByteType>(imp);

		// display it via ImageJ
		//imp.show();

		// wrap it into an ImgLib image (no copying)
		//final Img<T> image = ImagePlusAdapter.wrap(imp);
		// display it via ImgLib using ImageJ
		//ImageJFunctions.show(image);

		Img<UnsignedByteType> img = IO.openImgs(file.getAbsolutePath(), new UnsignedByteType()).get(0);
		ImageJFunctions.show(img);
		
		int width = (int) img.dimension(0);
		int height = (int) img.dimension(1);
		int size = width * height;
		IJ.log("size = " + size);
		
		int delta = 15;
		int minSize = (int) (0.001 * size);
		int maxSize = (int) (0.25 * size);
		double maxVariation = 3;
		double minDiversity = 0.4;
		boolean darkToBright= true;

		// --------------------------------------------------------------------------------------------------------
		MserTree<UnsignedByteType> newtree = 
				MserTree.buildMserTree(img, delta, minSize, maxSize, maxVariation, minDiversity, darkToBright);
		// --------------------------------------------------------------------------------------------------------
		IJ.log("done");
		
		ImagePlus imPlus = ImageJFunctions.wrapUnsignedByte(img, "title");
        ImageProcessor ip = imPlus.getProcessor().convertToColorProcessor();
        
        //IJ.log("ip = " + ip);
		
        
		int k = 0;
		for (Mser<UnsignedByteType> mser :  newtree) {
			{
				double[] mean = mser.mean();
				int x = (int) Math.rint(mean[0]);
				int y = (int) Math.rint(mean[1]);
				int rad = (int) (0.25 * Math.sqrt(mser.size()));
				ip.setColor(Color.red);
				ip.drawOval(x - rad, y - rad, 2*rad, 2*rad);
				//IJ.log(k + ": " + Arrays.toString(mean));
				IJ.log(String.format("%d: value=%d size=%d score=%.2f childs=%d", 
						k, mser.value().getInteger(), mser.size(), mser.score(), mser.getChildren().size()));
				double cov[] = mser.cov();
			}
			
//			ip.setColor(Color.green);
//			for (Mser<UnsignedByteType> child : mser.getChildren()) {
//				double[] mean = mser.mean();
//				int x = (int) Math.rint(mean[0]);
//				int y = (int) Math.rint(mean[1]);
//				int rad = (int) (0.25 * Math.sqrt(mser.size()));
//				
//				ip.drawOval(x - rad, y - rad, 2*rad, 2*rad);
//				IJ.log(String.format("     %d: value=%d size=%d score=%.2f childs=%d", 
//						k, child.value().getInteger(), child.size(), child.score(), child.getChildren().size()));
//			}
			k++;
		}
		
		new ImagePlus("MSER", ip).show();
		
		
//		Img<UnsignedByteType> img2 = img.factory().create(img);
//        ImageJFunctions.show(img2);

        //ImagePlus imPlus = ImageJFunctions.showUnsignedByte(img);
     
        //System.exit(0);
	}
	
	

	public static void main(String[] args) {
		// open an ImageJ window
		new ImageJ();

		// run the example
		new MSER_imglib2_WB();
	}
}

/*
 * Example: "D://images/boats.png"
size = 414720
done
0: value=59 size=5072 score=0,01 childs=0
1: value=59 size=2237 score=0,16 childs=0
2: value=65 size=1185 score=0,25 childs=0
3: value=63 size=657 score=0,78 childs=0
4: value=84 size=774 score=0,44 childs=0
5: value=77 size=2208 score=0,47 childs=2
6: value=102 size=555 score=0,30 childs=0
7: value=94 size=563 score=0,30 childs=0
8: value=89 size=8715 score=0,09 childs=2
9: value=90 size=4179 score=0,48 childs=2
10: value=89 size=1708 score=0,15 childs=0
11: value=98 size=427 score=0,30 childs=0
12: value=40 size=2202 score=0,30 childs=0
13: value=44 size=512 score=0,32 childs=0
14: value=14 size=938 score=0,00 childs=0
15: value=30 size=648 score=1,00 childs=0
16: value=97 size=493 score=0,42 childs=0
17: value=65 size=1330 score=0,36 childs=0
18: value=73 size=1137 score=0,07 childs=0
19: value=63 size=660 score=0,49 childs=0
20: value=53 size=21868 score=0,28 childs=2
21: value=53 size=25639 score=0,11 childs=2
22: value=54 size=1541 score=0,49 childs=0
23: value=73 size=676 score=0,10 childs=0
24: value=65 size=1160 score=0,12 childs=0
25: value=78 size=1964 score=0,42 childs=2
26: value=76 size=595 score=0,39 childs=0
27: value=108 size=461 score=0,25 childs=0
28: value=99 size=561 score=0,27 childs=0
29: value=100 size=1724 score=0,15 childs=1
30: value=88 size=732 score=0,07 childs=0
31: value=110 size=21042 score=0,25 childs=6
32: value=116 size=490 score=0,86 childs=0
33: value=128 size=511 score=0,71 childs=0
34: value=101 size=1403 score=0,25 childs=0
35: value=103 size=72634 score=0,12 childs=7
36: value=124 size=3302 score=0,15 childs=1
37: value=118 size=624 score=0,13 childs=0
38: value=120 size=663 score=0,23 childs=0
39: value=120 size=661 score=0,21 childs=0
40: value=116 size=3721 score=0,35 childs=3
41: value=115 size=2691 score=0,24 childs=1

*/
