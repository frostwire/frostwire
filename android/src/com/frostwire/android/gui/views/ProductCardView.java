package com.frostwire.android.gui.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.offers.Product;
import com.frostwire.logging.Logger;

/**
 * Created on 7/7/16.
 * @author gubatron
 * @author marcelinkaaa
 * @author aldenml
 *
 */
public class ProductCardView extends LinearLayout {
    @SuppressWarnings("unused")
    private static Logger LOGGER = Logger.getLogger(ProductCardView.class);
    private final String titleBold;
    private final String titleNormal;
    private String price;
    private String description;
    private final String hintButtonCaption;
    private final boolean selected;
    private final boolean hintButtonVisible;

    public ProductCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (isInEditMode()) {
            titleBold = "[Title Bold]";
            titleNormal = "[dummy title]";
            price = "$0.99";
            description = "[dummy description]";
            hintButtonCaption = "[dummy hint]";
            selected = hintButtonVisible = false;
            return;
        }
        TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ProductCardView, 0, 0);
        titleBold = attributes.getString(R.styleable.ProductCardView_product_card_title_bold);
        titleNormal = attributes.getString(R.styleable.ProductCardView_product_card_title_normal);
        price = attributes.getString(R.styleable.ProductCardView_product_card_price);
        description = attributes.getString(R.styleable.ProductCardView_product_card_description);
        selected = attributes.getBoolean(R.styleable.ProductCardView_product_card_selected, false);
        hintButtonVisible = attributes.getBoolean(R.styleable.ProductCardView_product_card_hint_button_visible, false);
        hintButtonCaption = attributes.getString(R.styleable.ProductCardView_product_card_hint_button_caption);
        attributes.recycle();
    }

    public void updateData(Product p) {
        String currency = p.currency();
        String productPrice = p.price();
        String productDescription = p.description();
        if (currency != null && productPrice != null) {
            price = currency + " " + productPrice;
        }
        if (productDescription != null) {
            description = productDescription;
        }
        initComponents();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_product_card, this);
        setSelected(selected);
        invalidate();
        initComponents();
    }

    /**
     * Replaces the card's background to make it look selected/not selected.
     * @param selected
     */
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        setBackground(selected ? R.drawable.product_card_background_selected : R.drawable.product_card_background);
    }

    private void setBackground(int id) {
        getChildAt(0).setBackgroundResource(id);
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