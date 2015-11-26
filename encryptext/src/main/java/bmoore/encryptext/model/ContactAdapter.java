package bmoore.encryptext.model;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;

import bmoore.encryptext.R;

public class ContactAdapter extends ArrayAdapter<Contact>
{
	Context context;
	ArrayList<Contact> data = null;
	int layoutResourceId;
	Filter nameFilter = new Filter()
	{
		public String convertResultToString(Object paramAnonymousObject)
		{
			return ((Contact)paramAnonymousObject).getName();
		}

		protected FilterResults performFiltering(CharSequence paramAnonymousCharSequence)
		{
			FilterResults localFilterResults = new FilterResults();
			localFilterResults.values = ContactAdapter.this.data;
			localFilterResults.count = ContactAdapter.this.data.size();
			return localFilterResults;
		}

		protected void publishResults(CharSequence paramAnonymousCharSequence, FilterResults paramAnonymousFilterResults)
		{
			ContactAdapter.this.notifyDataSetChanged();
		}
	};

	public ContactAdapter(Context paramContext, int paramInt, ArrayList<Contact> paramArrayList)
	{
		super(paramContext, paramInt, paramArrayList);
		this.layoutResourceId = paramInt;
		this.context = paramContext;
		this.data = paramArrayList;
	}

	public Filter getFilter()
	{
		return this.nameFilter;
	}

	public View getView(int pos, View row, ViewGroup parent)
	{
		if(row == null)
		{
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			row = inflater.inflate(R.layout.contact, parent, false);
		}
		
		TextView name = (TextView)row.findViewById(R.id.Name);
		TextView number = (TextView)row.findViewById(R.id.Address);
        ImageView thumb = (ImageView)row.findViewById(R.id.Thumb);
        ImageView key = (ImageView) row.findViewById(R.id.Key);
		
		name.setText(data.get(pos).getName());
		number.setText(data.get(pos).getNumber());
        thumb.setImageBitmap(data.get(pos).getThumb());
        key.setImageAlpha(data.get(pos).getAlpha());
		return row;
	}
}

/* Location:           C:\Users\Benjamin Moore\Dropbox\App\Code Recovery\classes_dex2jar.jar
 * Qualified Name:     com.encryptext.ContactAdapter
 * JD-Core Version:    0.6.2
 */