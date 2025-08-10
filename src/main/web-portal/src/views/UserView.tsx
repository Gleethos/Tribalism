import React from 'react';
import {useVal, useVar} from '../hooks/useProperty';
import Input from '../components/atoms/Input';
import Button from '../components/atoms/Button';
import { GiShamblingZombie, GiRaiseZombie } from 'react-icons/gi';


function UserView({ vm }: any) {
    const [username, setUsername]   = useVar(vm, vm => vm.username(), '');
    const [password, setPassword]   = useVar(vm, vm => vm.password(), '');

    return (
        <div className=' bg-no-repeat bg-cover xl:bg-contain bg-black bg-center bg-survival-1 h-[100vh] flex items-center justify-center flex-col gap-6'>
            <div
                className='flex justify-center items-center flex-col
            bg-gray-300/30 rounded-lg pt-6 pb-4 gap-6 px-10 w-full max-w-xl'
            >
                <h1 className='text-2xl text-gray-800 p-2 font-bold  border-b-2 border-slate-300 pb-4 mb-4'>
                    Welcome, {username}!
                </h1>
                <div className='login__username'>
                    <Input
                        inputStyles={'opacity-50 hover:opacity-100'}
                        labelStyles={'font-bold text-lg text-gray-800'}
                        labelOnTop={true}
                        type={'text'}
                        placeholder='Username'
                        value={username as string}
                        onChange={(event) => setUsername(event.target.value as string)}
                    />
                </div>
                <div className='login__password'>
                    <Input
                        inputStyles={'opacity-50 hover:opacity-100'}
                        labelStyles={'font-bold text-lg text-gray-800'}
                        labelOnTop={true}
                        type={'password'}
                        placeholder='Password'
                        value={password as string}
                        onChange={(event) => setPassword(event.target.value)}
                    />
                </div>
                <Button
                    buttonStyles='flex gap-3 items-center justify-center'
                    kind='primary'
                    onClick={() => vm.logout()}
                >
                    <span className='block'>Logout</span>
                    <GiShamblingZombie className='text-2xl' />
                </Button>
            </div>
        </div>
    );
}

export default UserView;
