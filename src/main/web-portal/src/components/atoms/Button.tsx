import React from 'react';

interface ButtonProps {
  kind: 'primary' | 'secondary';
  buttonStyles: string;
  onClick?: () => void;
  icon?: React.ReactNode;
  children?: React.ReactNode;
}

export default function Button({
  kind,
  buttonStyles,
  onClick,
  icon,
  children,
}: ButtonProps) {
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
