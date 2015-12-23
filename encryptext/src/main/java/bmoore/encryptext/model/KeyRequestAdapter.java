package bmoore.encryptext.model;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import bmoore.encryptext.R;
import bmoore.encryptext.utils.DateUtils;

/**
 * Created by Benjamin Moore on 11/23/2015.
 */
public class KeyRequestAdapter extends ArrayAdapter<KeyRequest> {
    Context context;
    ArrayList<KeyRequest> data = null;
    int layoutResourceId;

    public KeyRequestAdapter(Context context, int layoutResourceId, ArrayList<KeyRequest> keyRequests) {
        super(context, layoutResourceId, keyRequests);
        this.layoutResourceId = layoutResourceId;
        this.context = context;
        data = keyRequests;
    }

    @Override
    public View getView(int pos, View row, ViewGroup parent) {
        if (row == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            row = inflater.inflate(R.layout.key_exchange_request_listitem, parent, false);
        }

        TextView name = (TextView) row.findViewById(R.id.key_exhange_request_listitem_name);
        TextView status = (TextView) row.findViewById(R.id.key_exchange_request_listitem_status);
        ImageView contactThumb = (ImageView) row.findViewById(R.id.key_exchange_request_listitem_contact_thumb);
        TextView date = (TextView) row.findViewById(R.id.key_exhange_request_listitem_date);

        KeyRequest request = data.get(pos);

        name.setText(request.getName());

        String statusFriendly = "";

        if (Contact.KeyStatus.NEEDS_REVIEW.toString().equals(request.getStatus()))
            statusFriendly = "Tap to review";
        else if (Contact.KeyStatus.REQUEST_SENT.toString().equals(request.getStatus()))
            statusFriendly = "Request sent";

        if (request.getStatus() != null && request.getDate() != null)
            status.setText(statusFriendly);

        contactThumb.setImageBitmap(request.getContactThumb());

        date.setText(DateUtils.formatDate(request.getDate()));

        return row;
    }

    public List<KeyRequest> getData() {
        return data;
    }
}
