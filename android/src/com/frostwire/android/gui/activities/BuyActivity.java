/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.activities;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.ProductCardView;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.android.offers.Product;
import com.frostwire.android.offers.Products;

/**
 * @author gubatron
 * @author aldenml
 */
public class BuyActivity extends AbstractActivity {

    private ProductCardView card30days;
    private ProductCardView card1year;
    private ProductCardView card6months;

    public BuyActivity() {
        super(R.layout.activity_buy);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        ActionBar bar = getActionBar();
        if (bar != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setIcon(android.R.color.transparent);
        }

        card30days = findView(R.id.activity_buy_product_card_30_days);
        card1year = findView(R.id.activity_buy_product_card_1_year);
        card6months = findView(R.id.activity_buy_product_card_6_months);

        View.OnTouchListener cardTouchListener = createCardTouchListener();
        card30days.setOnTouchListener(cardTouchListener);
        card1year.setOnTouchListener(cardTouchListener);
        card6months.setOnTouchListener(cardTouchListener);

        Product p;
        p = PlayStore.getInstance().product(Products.INAPP_DISABLE_ADS_1_MONTH_SKU);
        setupButton(R.id.activity_buy_button_1, p);
        p = PlayStore.getInstance().product(Products.INAPP_DISABLE_ADS_6_MONTHS_SKU);
        setupButton(R.id.activity_buy_button_2, p);
        p = PlayStore.getInstance().product(Products.INAPP_DISABLE_ADS_1_YEAR_SKU);
        setupButton(R.id.activity_buy_button_3, p);
        p = PlayStore.getInstance().product(Products.SUBS_DISABLE_ADS_1_MONTH_SKU);
        setupButton(R.id.activity_buy_button_4, p);
        p = PlayStore.getInstance().product(Products.SUBS_DISABLE_ADS_6_MONTHS_SKU);
        setupButton(R.id.activity_buy_button_5, p);
        p = PlayStore.getInstance().product(Products.SUBS_DISABLE_ADS_1_YEAR_SKU);
        setupButton(R.id.activity_buy_button_6, p);
    }

    void on30DaysCardTouched() {
        card30days.setSelected(true);
        card1year.setSelected(false);
        card6months.setSelected(false);
    }

    void on1YearCardTouched() {
        card30days.setSelected(false);
        card1year.setSelected(true);
        card6months.setSelected(false);
    }

    void on6MonthsCardTouched() {
        card30days.setSelected(false);
        card1year.setSelected(false);
        card6months.setSelected(true);
    }

    private View.OnTouchListener createCardTouchListener() {
        return new ProductCardViewOnTouchListener();
    }

    private class ProductCardViewOnTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (v instanceof ProductCardView) {
                int id = v.getId();
                switch (id) {
                    case R.id.activity_buy_product_card_30_days:
                        BuyActivity.this.on30DaysCardTouched();
                        break;
                    case R.id.activity_buy_product_card_1_year:
                        BuyActivity.this.on1YearCardTouched();
                        break;
                    case R.id.activity_buy_product_card_6_months:
                        BuyActivity.this.on6MonthsCardTouched();
                        break;
                }
            }
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        PlayStore store = PlayStore.getInstance();
        if (store.handleActivityResult(requestCode, resultCode, data)) {
            store.refresh();
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setupButton(int id, final Product p) {
        Button b = findView(id);
        if (p == null) {
            b.setText("NA");
            b.setEnabled(false);
            return;
        }

        b.setText("title:" + p.title() + "\n" + "desc:" +
                p.description() + "\n" + "price:" + p.price() + "purchased:" + p.purchased());
        if (p.available()) {
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PlayStore.getInstance().purchase(BuyActivity.this, p);
                }
            });
        } else {
            b.setEnabled(false);
        }
    }
}
