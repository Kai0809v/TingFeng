// 修改后的 UserAdapter.java
package com.dreamct.tingfeng.utilities;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dreamct.tingfeng.data.User;
import com.dreamct.tingfeng.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
    private List<User> users = new ArrayList<>();
    // 使用Set提高查找效率
    private Set<Integer> selectedPositions = new HashSet<>();

    public List<User> getUsers() {
        return users;
    }
    /** 编辑模式标志位*/
    private boolean isEditMode = false;
    /** 设置编辑模式的方法 */
    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        if (!editMode) {
            clearSelected(); // 退出编辑模式时清空选中状态
        }
        notifyDataSetChanged(); // 刷新所有item的复选框可见性
    }


    public void setUsers(List<User> users) {
        this.users.clear();
        this.users.addAll(users);
        clearSelected();  // 数据更新时清空选择
        notifyDataSetChanged();
    }

    // 添加数据追加方法
    public void appendUsers(List<User> newUsers) {
        int startPos = users.size();
        users.addAll(newUsers);
        notifyItemRangeInserted(startPos, newUsers.size());
    }

    public List<User> getSelectedUsers() {
        List<User> selected = new ArrayList<>();
        for (int position : selectedPositions) {
            if (position < users.size()) {
                selected.add(users.get(position));
            }
        }
        return selected;
    }

    public void clearSelected() {
        Set<Integer> oldSelections = new HashSet<>(selectedPositions);
        selectedPositions.clear();
        // 只刷新之前选中的项目
        for (int position : oldSelections) {
            notifyItemChanged(position);
        }
    }

    public void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }
        notifyItemChanged(position);
    }

    private OnItemLongClickListener longClickListener;

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_users, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = users.get(position);
        holder.tvAccount.setText(user.account);
        holder.tvId.setText("密码: " + user.password);

        // 优化：使用position判断选中状态
        boolean isSelected = selectedPositions.contains(position);
        holder.checkBox.setVisibility(isEditMode ? View.VISIBLE : View.GONE);  // 编辑模式下显示复选框
        holder.checkBox.setChecked(isSelected);

        // 优化：使用已存在的监听器
        holder.itemView.setOnClickListener(v -> {
            if (isEditMode) {  // 仅编辑模式下响应点击选中
                toggleSelection(position);
            }
        });
        holder.itemView.setOnLongClickListener(v ->
                longClickListener != null &&
                        longClickListener.onItemLongClick(v, position)
        );
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onItemClick(position);
            }
        });
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(View view, int position);
    }
    // 在 UserAdapter 中添加
    private OnItemClickListener clickListener;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }



    @Override
    public int getItemCount() {
        return users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvAccount, tvId;
        CheckBox checkBox;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);//预留头像视图，以后会用到
            tvAccount = itemView.findViewById(R.id.tv_account);
            tvId = itemView.findViewById(R.id.tv_mima);
            checkBox = itemView.findViewById(R.id.check_box);

        }
    }
}