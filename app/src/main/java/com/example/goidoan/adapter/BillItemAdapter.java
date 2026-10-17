package com.example.goidoan.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.goidoan.R;
import com.example.goidoan.model.OrderItem;

import java.util.ArrayList;
import java.util.List;

public class BillItemAdapter extends RecyclerView.Adapter<BillItemAdapter.BillViewHolder> {

    private final Context context;
    private List<OrderItem> billList = new ArrayList<>();

    public BillItemAdapter(Context context) {
        this.context = context;
    }

    public void setBillItems(List<OrderItem> items) {
        this.billList = items != null ? new ArrayList<>(items) : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BillViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_bill_row, parent, false);
        return new BillViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BillViewHolder holder, int position) {
        OrderItem item = billList.get(position);

        if (item.getMenuItem() != null) {
            holder.tvBillDishName.setText(item.getMenuItem().getName());
        }
        holder.tvBillQuantity.setText("x" + item.getQuantity());
        holder.tvBillLineTotal.setText(item.getFormattedTotalPrice());

        if (item.getNote() != null && !item.getNote().isEmpty()) {
            holder.tvBillDishNote.setVisibility(View.VISIBLE);
            holder.tvBillDishNote.setText(item.getNote());
        } else {
            holder.tvBillDishNote.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return billList.size();
    }

    static class BillViewHolder extends RecyclerView.ViewHolder {
        TextView tvBillDishName, tvBillQuantity, tvBillLineTotal, tvBillDishNote;

        BillViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBillDishName = itemView.findViewById(R.id.tvBillDishName);
            tvBillQuantity = itemView.findViewById(R.id.tvBillQuantity);
            tvBillLineTotal = itemView.findViewById(R.id.tvBillLineTotal);
            tvBillDishNote = itemView.findViewById(R.id.tvBillDishNote);
        }
    }
}
