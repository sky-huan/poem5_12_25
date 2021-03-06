package com.example.poem5_12_25.activity;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import butterknife.BindView;
import butterknife.ButterKnife;

import com.example.poem5_12_25.GlobalObject;
import com.example.poem5_12_25.R;
import com.example.poem5_12_25.cache.LastPoemCache;
import com.example.poem5_12_25.dao.FavorityPoemDao;
import com.example.poem5_12_25.dao.PoemDao;
import com.example.poem5_12_25.database.Database1;
import com.example.poem5_12_25.entity.FavorityPoem;
import com.example.poem5_12_25.entity.Poem;
import com.example.poem5_12_25.menu.MainMenu;
import com.example.poem5_12_25.pojo.PoemPojo;
import com.example.poem5_12_25.utils.http.HttpRequestUtil;
import com.example.poem5_12_25.utils.http.tool.HttpRequestData;
import com.example.poem5_12_25.utils.http.tool.HttpResponseData;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.jaeger.library.StatusBarUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class PoemActivity extends BaseActivity {

    private static final String TAG = "PoemActivity";

    @BindView(R.id.iv_header)
    ImageView ivHeader;
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.ctl_toolbar)
    CollapsingToolbarLayout ctlToolbar;
    @BindView(R.id.appbar)
    AppBarLayout appbar;
    @BindView(R.id.tv_title)
    TextView tvTitle;
    @BindView(R.id.tv_author)
    TextView tvAuthor;
    @BindView(R.id.tv_content)
    TextView tvContent;
    @BindView(R.id.sv_content)
    ScrollView svContent;
    @BindView(R.id.fab_refresh)
    FloatingActionButton fabRefresh;
    @BindView(R.id.fab_favorite)
    FloatingActionButton fabFavorite;
    @BindView(R.id.coordinatorLayout)
    CoordinatorLayout coordinatorLayout;

    private Context context = this;

    private Poem currentPoem;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 状态栏颜色处理
        StatusBarUtil.setTransparent(PoemActivity.this);
        setContentView(R.layout.activity_poem);

        ButterKnife.bind(this);

        ViewCompat.setNestedScrollingEnabled(svContent, true);
        setSupportActionBar(toolbar);

        // 点击刷新按钮诗句
        fabRefresh.setOnClickListener(view ->
        {
            new GetPoemTask().execute(201L);
            LastPoemCache.setLastPoem(currentPoem);
        });

        // 打开时显示诗文设置
        Intent intent = getIntent();
        if (intent.getBooleanExtra("local", false)) {
            String content = intent.getStringExtra("content");
            String substring = content.substring(1, content.length() - 1);
            Poem poem = new Poem(
                    intent.getIntExtra("id", 0),
                    intent.getStringExtra("author"),
                    intent.getStringExtra("title"),
                    substring
                    );
            setCurrentPoem(poem);
        } else {
            // 打开自动刷新一首诗
            Poem lastPoem = LastPoemCache.getLastPoem();
            if (lastPoem == null)
                new GetPoemTask().execute(201L);
            else
                setCurrentPoem(lastPoem);
        }

        // 点击收藏按钮
        fabFavorite.setOnClickListener(v -> {
            GlobalObject.submitTask(() -> {
                FavorityPoemDao favorityPoemDao = Database1.getInstance(context).FavorityPoemDao();
                PoemDao poemDao = Database1.getInstance(context).PoemDao();
                Poem poemFavoriteEntity = new Poem(
                        currentPoem.getId(),
                        currentPoem.getAuthor(),
                        currentPoem.getName(),
                        currentPoem.getContentCsv()
                );
                // 注意: 这里默认将本地唯一登录用户的id设置为1,但是其实应该保存到云端.然后每次登陆时,从云端获取数据.
                FavorityPoem favorityPoem = new FavorityPoem(poemFavoriteEntity.getId(),1);
                if (favorityPoemDao.selectFavorityPoemIsExistByPid(poemFavoriteEntity.getId()) == null) {
                    // 数据应该上传到云端
                    favorityPoemDao.insertFavorityPoem(favorityPoem);
                    poemDao.insertPoem(poemFavoriteEntity);
                    Snackbar.make(coordinatorLayout, String.format("《%s》已加入收藏夹", tvTitle.getText()), Snackbar.LENGTH_SHORT).show();
                } else {
                    favorityPoemDao.deleteFavorityPoem(poemFavoriteEntity.getId());
                    poemDao.deletePoem(poemFavoriteEntity);
                    Snackbar.make(coordinatorLayout, String.format("《%s》已经从收藏夹移除", tvTitle.getText()), Snackbar.LENGTH_SHORT).show();
                }
            });

        });

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 设置菜单
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    // 菜单点击事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return new MainMenu(this).onOptionsItemSelected(item);
    }



    /**
     * 设置当前的古诗
     *
     * @param poem Poem对象
     */
    private void setCurrentPoem(Poem poem) {
        currentPoem = poem;
        tvAuthor.setText(poem.getAuthor());
        tvTitle.setText(poem.getName());
        // 根据不同安卓系统版本做的优化
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            tvContent.setText(String.join("\n", poem.getContent()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            tvContent.setText(poem.getContent().stream().collect(Collectors.joining("\n")));
        } else {
            StringBuilder joinContent = new StringBuilder();
            for (String s : poem.getContent()) {
                joinContent.append(s).append("\n");
            }
            // 删除最后一个换行符
            joinContent.deleteCharAt(joinContent.length() - 1);
            tvContent.setText(joinContent);
        }
    }

    /**
     * 异步加载刷新古诗
     */
    class GetPoemTask extends AsyncTask<Long, Integer, PoemPojo> {

        @Override
        protected PoemPojo doInBackground(Long... longs) {
            return HttpRequestUtil.getHttpPoem(longs[0]);
        }

        @Override
        protected void onPostExecute(PoemPojo data) {
            super.onPostExecute(data);
            System.out.println(data);
            if (data != null) {
                Poem poem = new Poem(data);
                setCurrentPoem(poem);
            } else
                Snackbar.make(coordinatorLayout, "网络连接失败！", Snackbar.LENGTH_SHORT).show();
        }
    }


//    // 加载顶部图片任务
//    class GetHeaderImageTask extends AsyncTask<Void, Integer, HttpResponseData> {
//        @Override
//        protected HttpResponseData doInBackground(Void... voids) {
//            return HttpRequestUtil.getImage(new HttpRequestData("https://picsum.photos/1080/675?image=957&random&blur"));
//        }
//
//        @Override
//        protected void onPostExecute(HttpResponseData data) {
//            super.onPostExecute(data);
//            if (data.success) {
//                ivHeader.setImageBitmap(data.bitmap);
//            }
//        }
//    }


}
