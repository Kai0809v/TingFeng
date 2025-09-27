package com.dreamct.tingfeng.data;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dreamct.tingfeng.R;
import com.dreamct.tingfeng.utilities.UserAdapter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UserListActivity extends AppCompatActivity {
    private UserAdapter adapter;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final int PAGE_SIZE = 20;
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private boolean isEditMode = false;
    private Button btnDelete;
    private LinearLayout rqEmpty; // 空数据提示TextView的引用
    private RecyclerView recyclerView;
    //final关键字用于表示一个变量只能被赋值一次

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_users);

        //RecyclerView
        recyclerView = findViewById(R.id.recycler_view);
        btnDelete = findViewById(R.id.btn_delete);
        rqEmpty = findViewById(R.id.RQ_empty);

        adapter = new UserAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // 分页滚动监听
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                //assert layoutManager != null;
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                // 优化加载条件
                if (!isLoading && hasMore && dy > 0) {
                    if ((firstVisibleItemPosition + visibleItemCount) >= totalItemCount
                            && totalItemCount >= PAGE_SIZE) {
                        loadUsers(currentPage + 1);
                    }
                }
            }
        });

        adapter.setOnItemLongClickListener((view, position) -> {
            if (!isEditMode) {
                enterEditMode();
                adapter.toggleSelection(position); // 自动选中长按项
            }
            return true;
        });

        adapter.setOnItemClickListener(position -> {
            if (isEditMode) {
                adapter.toggleSelection(position);
            } else {
                // 正常点击操作（例如打开详情）
                openUserDetail(adapter.getUsers().get(position));
            }
        });

        loadUsers(currentPage);
        btnDelete.setOnClickListener(v -> deleteSelectedUsers());
    }

    private void enterEditMode() {
        isEditMode = true;
        btnDelete.setVisibility(View.VISIBLE);
        adapter.setEditMode(true);
    }

    private void exitEditMode() {
        isEditMode = false;
        btnDelete.setVisibility(View.GONE);
        adapter.setEditMode(false);
        adapter.clearSelected();
    }

    private void deleteSelectedUsers() {
        List<User> selectedUsers = adapter.getSelectedUsers();
        if (selectedUsers.isEmpty()) return;

        executor.execute(() -> {//下方，AppDatabase由Java转为Kotlin后，需要添加成员Companion，详情见AppDatabase
            AppDatabase.Companion.getInstance(this)
                    .userDao()
                    .deleteUsers(selectedUsers);

            runOnUiThread(() -> {
                // 计算删除后是否影响分页
                int deleteCount = selectedUsers.size();
                int currentItems = adapter.getItemCount();

                if (deleteCount >= currentItems && currentPage > 0) {
                    // 如果删除了当前页所有内容，回退到前一页
                    loadUsers(currentPage - 1);
                } else {
                    // 否则重新加载当前页
                    loadUsers(currentPage);
                }

                exitEditMode();
            });
        });
    }

    private void loadUsers(int page) {
        if (isLoading) return;

        isLoading = true;
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            int total = db.userDao().getTotalCount();
            int offset = page * PAGE_SIZE;

            if (offset < total) {
                List<User> users = db.userDao().getUsers(PAGE_SIZE, offset);
                runOnUiThread(() -> {
                    if (page == 0) {
                        adapter.setUsers(users);
                    } else {
                        adapter.appendUsers(users);
                    }
                    currentPage = page;
                    hasMore = (offset + PAGE_SIZE) < total;
                    isLoading = false;

                    // 空数据提示
                    if (users.isEmpty() && page == 0) {
                        showEmptyView();
                    } else {
                        hideEmptyView();
                    }
                });
            } else {
                runOnUiThread(() -> {
                    hasMore = false;
                    isLoading = false;
                    if (page == 0) showEmptyView();
                });
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (isEditMode) {
            exitEditMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    /** 实现空数据状态显示 */
    private void showEmptyView() {
        rqEmpty.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }
    /** 隐藏空数据状态*/
    private void hideEmptyView() {
        rqEmpty.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
    }

    private void openUserDetail(User user) {
        // 实现用户详情打开逻辑
    }

}