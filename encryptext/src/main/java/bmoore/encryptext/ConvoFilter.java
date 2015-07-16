package bmoore.encryptext;

import java.io.File;
import java.io.FileFilter;

class ConvoFilter
  implements FileFilter
{
  public boolean accept(File paramFile)
  {
    return paramFile.getName().endsWith("preview.dat");
  }
}

/* Location:           C:\Users\Benjamin Moore\Dropbox\App\Code Recovery\classes_dex2jar.jar
 * Qualified Name:     com.encryptext.ConvoFilter
 * JD-Core Version:    0.6.2
 */