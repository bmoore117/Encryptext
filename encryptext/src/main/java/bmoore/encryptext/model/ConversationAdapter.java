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
import bmoore.encryptext.utils.DateUtils;

public class ConversationAdapter extends ArrayAdapter<ConversationEntry>
{
	Context context;
	ArrayList<ConversationEntry> data = null;

	public ConversationAdapter(Context context, int layoutResourceId, ArrayList<ConversationEntry> data)
	{
		super(context, layoutResourceId, data);
		this.context = context;
		this.data = data;
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
		Date.setText(DateUtils.formatDate(data.get(pos).getDate()));

		return row;

	}

    public ArrayList<ConversationEntry> getData() { return data; }
}