package com.example.lbsdemo.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 角色选择适配器
 * 用于在下拉菜单中展示可选择的角色
 */
public class CharacterAdapter extends ArrayAdapter<Character> {
    private final LayoutInflater inflater;

    public CharacterAdapter(Context context, List<Character> characters) {
        super(context, 0, characters);
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createItemView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createItemView(position, convertView, parent);
    }

    private View createItemView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
        }

        TextView textView = (TextView) convertView;
        Character character = getItem(position);
        
        if (character != null) {
            textView.setText(character.getName());
        }

        return convertView;
    }
} 