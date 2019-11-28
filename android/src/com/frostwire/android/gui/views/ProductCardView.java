/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.offers.Product;

/**
 * @author gubatron
 * @author marcelinkaaa
 * @author aldenml
 */
public class ProductCardView extends RelativeLayout {

    private final String titleBold;
    private final String titleNormal;
    private String price;
    private String description;
    private final String hintButtonCaption;
    private final boolean selected;
    private final boolean hintButtonVisible;
    private PaymentOptionsVisibility paymentOptionsVisibility;

    public ProductCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.ProductCardView, 0, 0);
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

    public void updateTitle(String title) {
        TextView titleBoldTextView = findViewById(R.id.view_product_card_title_bold_portion);
        titleBoldTextView.setText(title);
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
     *
     * @param selected
     */
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        setBackgroundResource(selected ? R.drawable.product_card_background_selected : R.drawable.product_card_background);
    }

    public void setPaymentOptionsVisibility(PaymentOptionsVisibility optionsVisibility) {
        paymentOptionsVisibility = optionsVisibility;
    }

    PaymentOptionsVisibility getPaymentOptionsVisibility() {
        return paymentOptionsVisibility;
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
        TextView textView = findViewById(id);
        if (visible && value != null) {
            textView.setText(value);
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setVisibility(View.GONE);
        }
    }
}
