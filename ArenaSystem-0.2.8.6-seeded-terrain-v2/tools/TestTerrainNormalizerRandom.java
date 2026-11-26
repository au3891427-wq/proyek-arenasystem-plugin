package id.hadziq.arenasystem;
import java.util.Random;
public final class TestTerrainNormalizerRandom {
 public static void main(String[] args) {
  Random r=new Random(7);
  for(int test=0; test<250; test++) {
   int[][] h=new int[81][81]; int min=9999,max=-9999;
   for(int x=0;x<81;x++) for(int z=0;z<81;z++){h[x][z]=64+r.nextInt(45);min=Math.min(min,h[x][z]);max=Math.max(max,h[x][z]);}
   for (int offset : new int[]{27, 32}) {
    TerrainNormalizer.Result out=TerrainNormalizer.normalize(new TerrainProfile(40,h,min,max),1,offset);
    TerrainProfile p=out.profile();
    if(out.maxAdjacentStep()>1) throw new AssertionError("step");
    if(p.heightAt(-offset,0)!=p.heightAt(offset,0)) throw new AssertionError("spawn");
    int red=p.heightAt(-offset,0), blue=p.heightAt(offset,0);
    for(int dx=-4;dx<=4;dx++) for(int dz=-4;dz<=4;dz++)
     if(p.heightAt(-offset+dx,dz)!=red || p.heightAt(offset+dx,dz)!=blue) throw new AssertionError("pad flatness");
   }
  }
  System.out.println("250 random maps passed at offsets 27 and 32: adjacent delta <= 1 and RED/BLUE spawn pads equalized.");
 }
}
