package com.frostwire.android.gui.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.frostwire.android.R;

/**
 * Created on 7/7/16.
 * @author gubatron
 * @author marcelinkaaa
 * @author aldenml
 *
 */
public class ProductCard extends RelativeLayout {

    private final String titleBold;
    private final String titleNormal;
    private final String price;
    private final String description;
    private final String hintButtonCaption;
    private final boolean hintButtonVisible;

    public ProductCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (isInEditMode()) {
            titleBold = titleNormal = price = description = hintButtonCaption = null;
            hintButtonVisible = false;
            return;
        }
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.RichNotification);
        titleBold = attributes.getString(R.styleable.ProductCard_product_card_title_bold);
        titleNormal = attributes.getString(R.styleable.ProductCard_product_card_title_normal);
        price = attributes.getString(R.styleable.ProductCard_product_card_price);
        description = attributes.getString(R.styleable.ProductCard_product_card_description);
        hintButtonVisible = attributes.getBoolean(R.styleable.ProductCard_product_card_hint_button_visible, false);
        hintButtonCaption = attributes.getString(R.styleable.ProductCard_product_card_hint_button_caption);
        attributes.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_product_card, this);
        initComponents();
    }

    private void initComponents() {
        initTextView(R.id.view_product_card_title_bold_portion, titleBold);
        initTextView(R.id.view_product_card_title_normal_portion, titleNormal);
        initTextView(R.id.view_product_card_price, price);
        initTextView(R.id.view_product_card_description, description);
        initTextView(R.id.view_product_card_hint_button, hintButtonCaption, hintButtonVisible);
    }

    private void initTextView(int id, String value) {
        initTextView(id, value, true);
    }

    private void initTextView(int id, String value, boolean visible) {
        TextView textView = findView(id);
        if (visible && value != null) {
            textView.setText(value);
            textView.setVisibility(View.VISIBLE);
        }  else {
            textView.setVisibility(View.GONE);
        }
    }

    private <T> T findView(int id) {
        return (T) findViewById(id);
    }
}
