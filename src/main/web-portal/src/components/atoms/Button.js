import React from 'react';

export default function Button({
  kind,
  buttonStyles,
  onClick,
  icon,
  children,
}) {
  const classes =
    kind === 'primary'
      ? 'bg-gray-800 shadow-sm hover:shadow-lg hover:bg-gray-800/80 hover:scale-105 transition duration-300 text-white'
      : 'bg-gray-500 shadow-sm hover:shadow-lg hover:bg-gray-500/80 hover:scale-105 transition duration-300 text-white';

  return (
    <button
      onClick={onClick}
      className={`${buttonStyles} px-4 py-3 rounded-lg ${classes}`}
    >
      {children}
    </button>
  );
}
