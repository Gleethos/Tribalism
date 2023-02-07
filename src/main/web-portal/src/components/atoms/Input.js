import React from 'react';

export default function Input({
  inputStyles,
  placeholder,
  type,
  value,
  onChange,
  labelOnTop,
  labelStyles,
  id,
}) {
  return (
    <div className={`flex gap-2 items-center ${labelOnTop ? 'flex-col' : ''}`}>
      <label
        className={`${labelOnTop ? 'block' : ''} ${labelStyles}`}
        htmlFor={value}
      >
        {placeholder}
      </label>
      <input // Now we use the react state
        type={type}
        id={value}
        placeholder={placeholder}
        value={value}
        onChange={onChange}
        className={`rounded-lg px-4 py-2 transition-all duration-300 ${inputStyles}`}
      ></input>
    </div>
  );
}
