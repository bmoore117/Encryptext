package bmoore.encryptext.model;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import bmoore.encryptext.R;

public class ConversationAdapter extends ArrayAdapter<ConversationEntry>
{
	Context context;
	ArrayList<ConversationEntry> data = null;
	int layoutResourceId;
	Calendar c;

	public ConversationAdapter(Context paramContext, int paramInt, ArrayList<ConversationEntry> paramArrayList)
	{
		super(paramContext, paramInt, paramArrayList);
		this.layoutResourceId = paramInt;
		this.context = paramContext;
		this.data = paramArrayList;
		c = new GregorianCalendar(Locale.getDefault());
		c.setTime(new Date());
	}

	@Override
	public View getView(int pos, View row, ViewGroup parent)
	{

		if(row == null)
		{
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			row = inflater.inflate(R.layout.conversation_item, parent, false);
		}

		TextView From = (TextView) row.findViewById(R.id.conversation_item_from);
		TextView Message = (TextView) row.findViewById(R.id.conversation_item_message);
		ImageView Photo = (ImageView) row.findViewById(R.id.conversation_item_contact_thumb);
		TextView Date = (TextView) row.findViewById(R.id.conversation_item_date);

		From.setText(data.get(pos).getName());
		Message.setText(data.get(pos).getMessage());
		Photo.setImageBitmap(data.get(pos).getPhoto());
		Date.setText(formatDate(data.get(pos).getDate()));

		return row;

	}
	
	private String formatDate(String date)
	{
        //Log.i("Adapter", "Formatting date");
        if(date == null)
			return null;


        if("Sending".equals(date))
            return date;

        if(date.contains("*"))
        {

            int point = date.indexOf("*"); //remove asterisk padding
            date = date.substring(0, point);
            //Log.i("Adapter", "Trimmed date of " + date);
        }
		
		if(!date.contains(","))
			return date;
		
		String[] dateParts = date.split(",");
			
		if(Integer.decode(dateParts[3]) == c.get(Calendar.YEAR))
		{
			if(Integer.decode(dateParts[1]) == c.get(Calendar.MONTH) && Integer.decode(dateParts[2]) 
					== c.get(Calendar.DAY_OF_MONTH))
			{
                    //Log.i("Adapter", "Returning " + dateParts[0]);
					return dateParts[0]; //return time of day
			}

            //Log.i("Adapter", "Returning " + findMonth(dateParts[1]) + " " + dateParts[2]);
			return findMonth(dateParts[1]) + " " + dateParts[2]; //return day of month ex: May 17 
		}
		//Log.i("Adapter", "Returning " + findMonth(dateParts[1]) + " " + dateParts[2] + ", " + dateParts[3]);
		return findMonth(dateParts[1]) + " " + dateParts[2] + ", " + dateParts[3];
	}
	
	private String findMonth(String val)
	{

		if(val.equals("0"))
			return "Jan ";
		else if(val.equals("1"))
			return "Feb ";
		else if(val.equals("2"))
			return "Mar ";
		else if(val.equals("3"))
			return "Apr ";
		else if(val.equals("4"))
			return "May ";
		else if(val.equals("5"))
			return "Jun ";
		else if(val.equals("6"))
			return "Jul ";
		else if(val.equals("7"))
			return "Aug ";
		else if(val.equals("8"))
			return "Sep ";
		else if(val.equals("9"))
			return "Oct ";
		else if(val.equals("10"))
			return "Nov ";
		else
			return "Dec ";
	}

    public ArrayList<ConversationEntry> getData() { return data; }
}